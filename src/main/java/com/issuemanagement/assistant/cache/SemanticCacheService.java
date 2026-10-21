package com.issuemanagement.assistant.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SemanticCacheService {

    private final RedisTemplate<String, Object> assistantRedisTemplate;
    private final EmbeddingModel embeddingModel;
    private final SemanticCacheMetrics metrics;

    public SemanticCacheService(
            RedisTemplate<String, Object> assistantRedisTemplate,
            EmbeddingModel embeddingModel,
            SemanticCacheMetrics metrics) {
        this.assistantRedisTemplate = assistantRedisTemplate;
        this.embeddingModel = embeddingModel;
        this.metrics = metrics;
    }

    @Value("${assistant.semantic-cache.enabled:true}")
    private boolean enabled;

    @Value("${assistant.semantic-cache.ttl-seconds:3600}")
    private long ttlSeconds;

    @Value("${assistant.semantic-cache.similarity-threshold:0.88}")
    private double similarityThreshold;

    @Value("${assistant.semantic-cache.max-candidates:20}")
    private int maxCandidates;

    private static final String CACHE_KEY_PREFIX = "assistant:semantic_cache:entry:";
    private static final String CACHE_INDEX_KEY = "assistant:semantic_cache:index";

    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("(?i)\\b[A-Za-z]+-\\d+\\b");
    private static final Pattern STATUS_PATTERN = Pattern.compile("(?i)\\b(current status|status|state|is it open|is it closed|progress)\\b");
    private static final Pattern HISTORY_PATTERN = Pattern.compile("(?i)\\b(history|reopen|reopened|why was|who changed|timeline|changed during)\\b");
    private static final Pattern COMMENTS_PATTERN = Pattern.compile("(?i)\\b(comment|comments|discussion|notes|feedback)\\b");
    private static final Pattern USER_SPECIFIC_PATTERN = Pattern.compile("(?i)\\b(my\\s+(?:\\w+\\s+)?(?:issues|projects|tasks|workload)|assigned to me|i can access|mine)\\b");
    private static final Pattern OFF_TOPIC_CODE_PATTERN = Pattern.compile("(?i)\\b(write|create|generate|implement|give me|show me)\\s+(?:a\\s+|an\\s+)?(?:python|java|javascript|typescript|c\\+\\+|cpp|golang|rust|php|ruby|bash|shell|perl|html|css)\\s+(?:code|script|program|app|function|class|algorithm|snippet)\\b");

    public SemanticCacheEligibilityResult evaluateEligibility(String query) {
        if (!enabled) {
            return SemanticCacheEligibilityResult.ofBypass("CACHE_DISABLED");
        }
        if (query == null || query.isBlank() || query.trim().length() < 5) {
            return SemanticCacheEligibilityResult.ofBypass("QUERY_TOO_SHORT");
        }

        String normalized = query.trim();

        if (OFF_TOPIC_CODE_PATTERN.matcher(normalized).find()) {
            return SemanticCacheEligibilityResult.ofBypass("OFF_TOPIC_CODE_REQUEST");
        }
        if (ISSUE_KEY_PATTERN.matcher(normalized).find()) {
            return SemanticCacheEligibilityResult.ofBypass("SPECIFIC_ISSUE_KEY");
        }
        if (STATUS_PATTERN.matcher(normalized).find()) {
            return SemanticCacheEligibilityResult.ofBypass("DYNAMIC_STATUS");
        }
        if (HISTORY_PATTERN.matcher(normalized).find()) {
            return SemanticCacheEligibilityResult.ofBypass("DYNAMIC_HISTORY");
        }
        if (COMMENTS_PATTERN.matcher(normalized).find()) {
            return SemanticCacheEligibilityResult.ofBypass("DYNAMIC_COMMENTS");
        }
        if (USER_SPECIFIC_PATTERN.matcher(normalized).find()) {
            return SemanticCacheEligibilityResult.ofBypass("USER_SPECIFIC");
        }

        return SemanticCacheEligibilityResult.ofEligible();
    }

    public Optional<SemanticCacheEntry> findSimilarCachedResponse(String query) {
        if (!enabled) {
            return Optional.empty();
        }

        SemanticCacheEligibilityResult eligibility = evaluateEligibility(query);
        if (!eligibility.eligible()) {
            metrics.recordBypass(eligibility.bypassReason());
            return Optional.empty();
        }

        long startLookup = System.currentTimeMillis();
        try {
            long startEmbed = System.currentTimeMillis();
            float[] queryEmbedding = embeddingModel.embed(query);
            metrics.recordEmbeddingLatency(System.currentTimeMillis() - startEmbed);

            if (queryEmbedding == null || queryEmbedding.length == 0) {
                metrics.recordMiss(System.currentTimeMillis() - startLookup);
                return Optional.empty();
            }

            Set<Object> candidateIds = assistantRedisTemplate.opsForSet().members(CACHE_INDEX_KEY);
            if (candidateIds == null || candidateIds.isEmpty()) {
                metrics.recordMiss(System.currentTimeMillis() - startLookup);
                return Optional.empty();
            }

            SemanticCacheEntry bestMatch = null;
            double highestScore = -1.0;
            List<String> staleKeys = new ArrayList<>();

            for (Object idObj : candidateIds) {
                String entryId = idObj.toString();
                String key = CACHE_KEY_PREFIX + entryId;
                Object rawEntry = assistantRedisTemplate.opsForValue().get(key);

                if (rawEntry instanceof SemanticCacheEntry entry) {
                    double similarity = cosineSimilarity(queryEmbedding, entry.getEmbedding());
                    if (similarity >= similarityThreshold && similarity > highestScore) {
                        highestScore = similarity;
                        bestMatch = entry;
                    }
                } else {
                    // Stale or expired index entry
                    staleKeys.add(entryId);
                }
            }

            if (!staleKeys.isEmpty()) {
                assistantRedisTemplate.opsForSet().remove(CACHE_INDEX_KEY, staleKeys.toArray());
            }

            long lookupDuration = System.currentTimeMillis() - startLookup;
            if (bestMatch != null) {
                metrics.recordHit(lookupDuration);
                log.info("Semantic cache HIT for question (similarity: {})", Math.round(highestScore * 100.0) / 100.0);
                return Optional.of(bestMatch);
            } else {
                metrics.recordMiss(lookupDuration);
                return Optional.empty();
            }
        } catch (Exception ex) {
            log.warn("Semantic cache lookup failed gracefully: {}", ex.getMessage());
            metrics.recordError();
            return Optional.empty();
        }
    }

    public void cacheResponse(String query, String response, List<String> referencedKeys) {
        if (!enabled) {
            return;
        }

        SemanticCacheEligibilityResult eligibility = evaluateEligibility(query);
        if (!eligibility.eligible()) {
            return;
        }

        try {
            float[] embedding = embeddingModel.embed(query);
            if (embedding == null || embedding.length == 0) {
                return;
            }

            String cacheId = UUID.randomUUID().toString();
            SemanticCacheEntry entry = SemanticCacheEntry.builder()
                    .id(cacheId)
                    .originalQuery(query.trim())
                    .embedding(embedding)
                    .response(response)
                    .referencedIssueKeys(referencedKeys != null ? referencedKeys : List.of())
                    .scope("GENERAL")
                    .createdAt(Instant.now())
                    .ttlSeconds(ttlSeconds)
                    .build();

            String entryKey = CACHE_KEY_PREFIX + cacheId;
            assistantRedisTemplate.opsForValue().set(entryKey, entry, Duration.ofSeconds(ttlSeconds));
            assistantRedisTemplate.opsForSet().add(CACHE_INDEX_KEY, cacheId);

            // Cap the index size to avoid unbounded memory
            Long indexSize = assistantRedisTemplate.opsForSet().size(CACHE_INDEX_KEY);
            if (indexSize != null && indexSize > maxCandidates * 3) {
                pruneExpiredIndexEntries();
            }

            log.info("Cached safe response in semantic cache with ID {}", cacheId);
        } catch (Exception ex) {
            log.warn("Failed to save entry to semantic cache: {}", ex.getMessage());
            metrics.recordError();
        }
    }

    public void pruneExpiredIndexEntries() {
        try {
            Set<Object> candidateIds = assistantRedisTemplate.opsForSet().members(CACHE_INDEX_KEY);
            if (candidateIds == null) return;

            List<String> toRemove = new ArrayList<>();
            for (Object idObj : candidateIds) {
                String entryId = idObj.toString();
                Boolean exists = assistantRedisTemplate.hasKey(CACHE_KEY_PREFIX + entryId);
                if (Boolean.FALSE.equals(exists)) {
                    toRemove.add(entryId);
                }
            }
            if (!toRemove.isEmpty()) {
                assistantRedisTemplate.opsForSet().remove(CACHE_INDEX_KEY, toRemove.toArray());
            }
        } catch (Exception ex) {
            log.warn("Failed to prune semantic cache index: {}", ex.getMessage());
        }
    }

    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}

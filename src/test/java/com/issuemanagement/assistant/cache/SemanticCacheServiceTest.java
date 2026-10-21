package com.issuemanagement.assistant.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SemanticCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private SetOperations<String, Object> setOps;

    @Mock
    private EmbeddingModel embeddingModel;

    @Spy
    private SemanticCacheMetrics metrics = new SemanticCacheMetrics();

    @InjectMocks
    private SemanticCacheService cacheService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cacheService, "enabled", true);
        ReflectionTestUtils.setField(cacheService, "ttlSeconds", 3600L);
        ReflectionTestUtils.setField(cacheService, "similarityThreshold", 0.88);
        ReflectionTestUtils.setField(cacheService, "maxCandidates", 20);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    void testEligibility_BypassesSpecificIssueKeys() {
        SemanticCacheEligibilityResult r1 = cacheService.evaluateEligibility("Why was ISSUE-142 reopened?");
        assertThat(r1.eligible()).isFalse();
        assertThat(r1.bypassReason()).isEqualTo("SPECIFIC_ISSUE_KEY");

        SemanticCacheEligibilityResult r2 = cacheService.evaluateEligibility("What is the priority of PROJ-5?");
        assertThat(r2.eligible()).isFalse();
        assertThat(r2.bypassReason()).isEqualTo("SPECIFIC_ISSUE_KEY");
    }

    @Test
    void testEligibility_BypassesDynamicStatusAndHistory() {
        SemanticCacheEligibilityResult rStatus = cacheService.evaluateEligibility("What is the current status of the backend task?");
        assertThat(rStatus.eligible()).isFalse();
        assertThat(rStatus.bypassReason()).isEqualTo("DYNAMIC_STATUS");

        SemanticCacheEligibilityResult rHistory = cacheService.evaluateEligibility("Show me the history of changes");
        assertThat(rHistory.eligible()).isFalse();
        assertThat(rHistory.bypassReason()).isEqualTo("DYNAMIC_HISTORY");

        SemanticCacheEligibilityResult rComments = cacheService.evaluateEligibility("Summarize recent comments");
        assertThat(rComments.eligible()).isFalse();
        assertThat(rComments.bypassReason()).isEqualTo("DYNAMIC_COMMENTS");

        SemanticCacheEligibilityResult rUser = cacheService.evaluateEligibility("Show me my assigned issues");
        assertThat(rUser.eligible()).isFalse();
        assertThat(rUser.bypassReason()).isEqualTo("USER_SPECIFIC");
    }

    @Test
    void testEligibility_BypassesOffTopicCodeRequests() {
        SemanticCacheEligibilityResult r1 = cacheService.evaluateEligibility("write a python code to scrape a website");
        assertThat(r1.eligible()).isFalse();
        assertThat(r1.bypassReason()).isEqualTo("OFF_TOPIC_CODE_REQUEST");

        SemanticCacheEligibilityResult r2 = cacheService.evaluateEligibility("generate java code for binary search");
        assertThat(r2.eligible()).isFalse();
        assertThat(r2.bypassReason()).isEqualTo("OFF_TOPIC_CODE_REQUEST");
    }

    @Test
    void testEligibility_AcceptsGeneralExplanations() {
        SemanticCacheEligibilityResult r = cacheService.evaluateEligibility("How does issue priority work in this system?");
        assertThat(r.eligible()).isTrue();
        assertThat(r.bypassReason()).isNull();
    }

    @Test
    void testCosineSimilarity_Math() {
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v3 = new float[]{0.0f, 1.0f, 0.0f};

        assertThat(SemanticCacheService.cosineSimilarity(v1, v2)).isEqualTo(1.0);
        assertThat(SemanticCacheService.cosineSimilarity(v1, v3)).isEqualTo(0.0);
    }

    @Test
    void testFindSimilarCachedResponse_CacheHit() {
        String query = "How does issue priority work?";
        float[] embedding = new float[]{0.5f, 0.5f, 0.5f};
        when(embeddingModel.embed(query)).thenReturn(embedding);

        String entryId = "entry-1";
        when(setOps.members("assistant:semantic_cache:index")).thenReturn(Set.of(entryId));

        SemanticCacheEntry cachedEntry = SemanticCacheEntry.builder()
                .id(entryId)
                .originalQuery(query)
                .embedding(new float[]{0.5f, 0.5f, 0.5f}) // Identical -> similarity 1.0 >= 0.88
                .response("Issue priorities are LOW, MEDIUM, HIGH, and CRITICAL.")
                .referencedIssueKeys(List.of())
                .scope("GENERAL")
                .createdAt(Instant.now())
                .ttlSeconds(3600)
                .build();

        when(valueOps.get("assistant:semantic_cache:entry:" + entryId)).thenReturn(cachedEntry);

        Optional<SemanticCacheEntry> result = cacheService.findSimilarCachedResponse(query);

        assertThat(result).isPresent();
        assertThat(result.get().getResponse()).isEqualTo("Issue priorities are LOW, MEDIUM, HIGH, and CRITICAL.");
        assertThat(metrics.getMetricsSnapshot().get("cacheHits")).isEqualTo(1L);
        assertThat(metrics.getMetricsSnapshot().get("geminiCallsAvoided")).isEqualTo(1L);
    }

    @Test
    void testFindSimilarCachedResponse_CacheMissOnLowSimilarity() {
        String query = "How does issue priority work?";
        float[] queryEmbedding = new float[]{1.0f, 0.0f, 0.0f};
        when(embeddingModel.embed(query)).thenReturn(queryEmbedding);

        String entryId = "entry-dissimilar";
        when(setOps.members("assistant:semantic_cache:index")).thenReturn(Set.of(entryId));

        SemanticCacheEntry dissimilarEntry = SemanticCacheEntry.builder()
                .id(entryId)
                .originalQuery("Something else")
                .embedding(new float[]{0.0f, 1.0f, 0.0f}) // Orthogonal -> similarity 0.0 < 0.88
                .response("Something else entirely.")
                .build();

        when(valueOps.get("assistant:semantic_cache:entry:" + entryId)).thenReturn(dissimilarEntry);

        Optional<SemanticCacheEntry> result = cacheService.findSimilarCachedResponse(query);

        assertThat(result).isEmpty();
        assertThat(metrics.getMetricsSnapshot().get("cacheMisses")).isEqualTo(1L);
    }

    @Test
    void testFindSimilarCachedResponse_GracefulOutageHandling() {
        String query = "How does issue priority work?";
        when(embeddingModel.embed(query)).thenThrow(new RuntimeException("Embedding service offline"));

        Optional<SemanticCacheEntry> result = cacheService.findSimilarCachedResponse(query);

        assertThat(result).isEmpty();
        assertThat(metrics.getMetricsSnapshot().get("cacheErrors")).isEqualTo(1L);
    }

    @Test
    void testCacheResponse_StoresInRedisWhenEligible() {
        String query = "Explain workflow rules in detail";
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed(query)).thenReturn(embedding);

        cacheService.cacheResponse(query, "Workflow rules explanation", List.of());

        verify(valueOps).set(contains("assistant:semantic_cache:entry:"), any(SemanticCacheEntry.class), any(Duration.class));
        verify(setOps).add(eq("assistant:semantic_cache:index"), anyString());
    }

    @Test
    void testCacheResponse_BypassesWhenIneligible() {
        String query = "What is the status of ISSUE-100?";

        cacheService.cacheResponse(query, "Status is OPEN", List.of("ISSUE-100"));

        verifyNoInteractions(embeddingModel);
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }
}

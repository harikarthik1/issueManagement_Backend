package com.issuemanagement.assistant.cache;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SemanticCacheMetrics {

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong geminiCallsAvoided = new AtomicLong(0);
    private final AtomicLong cacheErrors = new AtomicLong(0);
    private final AtomicLong totalLookupLatencyMs = new AtomicLong(0);
    private final AtomicLong lookupCount = new AtomicLong(0);
    private final AtomicLong totalEmbeddingLatencyMs = new AtomicLong(0);
    private final AtomicLong embeddingCount = new AtomicLong(0);

    private final Map<String, AtomicLong> bypassesByReason = new ConcurrentHashMap<>();

    public void recordHit(long lookupLatencyMs) {
        cacheHits.incrementAndGet();
        geminiCallsAvoided.incrementAndGet();
        totalLookupLatencyMs.addAndGet(lookupLatencyMs);
        lookupCount.incrementAndGet();
    }

    public void recordMiss(long lookupLatencyMs) {
        cacheMisses.incrementAndGet();
        totalLookupLatencyMs.addAndGet(lookupLatencyMs);
        lookupCount.incrementAndGet();
    }

    public void recordBypass(String reason) {
        bypassesByReason.computeIfAbsent(reason, r -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordEmbeddingLatency(long latencyMs) {
        totalEmbeddingLatencyMs.addAndGet(latencyMs);
        embeddingCount.incrementAndGet();
    }

    public void recordError() {
        cacheErrors.incrementAndGet();
    }

    public Map<String, Object> getMetricsSnapshot() {
        long lookups = lookupCount.get();
        double avgLookupMs = lookups > 0 ? (double) totalLookupLatencyMs.get() / lookups : 0.0;

        long embeds = embeddingCount.get();
        double avgEmbedMs = embeds > 0 ? (double) totalEmbeddingLatencyMs.get() / embeds : 0.0;

        Map<String, Long> bypassCounts = new ConcurrentHashMap<>();
        bypassesByReason.forEach((k, v) -> bypassCounts.put(k, v.get()));

        return Map.of(
                "cacheHits", cacheHits.get(),
                "cacheMisses", cacheMisses.get(),
                "geminiCallsAvoided", geminiCallsAvoided.get(),
                "cacheErrors", cacheErrors.get(),
                "averageLookupLatencyMs", Math.round(avgLookupMs * 100.0) / 100.0,
                "averageEmbeddingLatencyMs", Math.round(avgEmbedMs * 100.0) / 100.0,
                "bypassesByReason", bypassCounts
        );
    }

    public void reset() {
        cacheHits.set(0);
        cacheMisses.set(0);
        geminiCallsAvoided.set(0);
        cacheErrors.set(0);
        totalLookupLatencyMs.set(0);
        lookupCount.set(0);
        totalEmbeddingLatencyMs.set(0);
        embeddingCount.set(0);
        bypassesByReason.clear();
    }
}

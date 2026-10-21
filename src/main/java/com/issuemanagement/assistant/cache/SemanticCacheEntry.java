package com.issuemanagement.assistant.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticCacheEntry implements Serializable {
    private String id;
    private String originalQuery;
    private float[] embedding;
    private String response;
    private List<String> referencedIssueKeys;
    private String scope;
    private Instant createdAt;
    private long ttlSeconds;
}

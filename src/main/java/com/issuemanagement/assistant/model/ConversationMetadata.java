package com.issuemanagement.assistant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMetadata implements Serializable {
    private String id;
    private String title;
    private Long userId;
    private String sessionId;
    private Instant createdAt;
    private Instant updatedAt;
    private int messageCount;
}

package com.issuemanagement.assistant.dto;

import java.time.Instant;
import java.util.List;

public record AssistantChatResponse(
        String conversationId,
        String answer,
        List<String> referencedIssueKeys,
        boolean cached,
        Instant timestamp
) {
    public AssistantChatResponse(String conversationId, String answer, List<String> referencedIssueKeys, boolean cached) {
        this(conversationId, answer, referencedIssueKeys != null ? referencedIssueKeys : List.of(), cached, Instant.now());
    }
}

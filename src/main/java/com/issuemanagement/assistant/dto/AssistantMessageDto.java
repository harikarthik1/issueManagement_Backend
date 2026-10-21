package com.issuemanagement.assistant.dto;

import java.time.Instant;
import java.util.List;

public record AssistantMessageDto(
        String role,
        String content,
        Instant timestamp,
        List<String> referencedIssueKeys
) {
    public AssistantMessageDto(String role, String content) {
        this(role, content, Instant.now(), List.of());
    }

    public AssistantMessageDto(String role, String content, List<String> referencedIssueKeys) {
        this(role, content, Instant.now(), referencedIssueKeys != null ? referencedIssueKeys : List.of());
    }
}

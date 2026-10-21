package com.issuemanagement.assistant.dto;

import java.time.Instant;

public record ConversationSummaryResponse(
        String id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        int messageCount
) {}

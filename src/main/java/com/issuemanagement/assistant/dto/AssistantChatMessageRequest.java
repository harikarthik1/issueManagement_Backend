package com.issuemanagement.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantChatMessageRequest(
        @NotBlank(message = "Message content must not be blank")
        @Size(max = 4000, message = "Message content exceeds maximum allowed length of 4000 characters")
        String message
) {}

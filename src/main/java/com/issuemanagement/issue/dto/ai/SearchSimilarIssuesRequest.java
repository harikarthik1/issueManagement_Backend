package com.issuemanagement.issue.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record SearchSimilarIssuesRequest(
        @NotBlank(message = "Description is required")
        String description
) {
}

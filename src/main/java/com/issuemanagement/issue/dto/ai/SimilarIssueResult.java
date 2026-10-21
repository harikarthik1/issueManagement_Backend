package com.issuemanagement.issue.dto.ai;

public record SimilarIssueResult(
        Long issueId,
        String issueKey,
        String title,
        String status,
        String priority,
        double similarityScore
) {
}

package com.issuemanagement.issue.dto.ai;

import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueType;

public record AiIssueDraftResponse(
        String title,
        String description,
        IssueType type,
        IssuePriority priority
) {
}

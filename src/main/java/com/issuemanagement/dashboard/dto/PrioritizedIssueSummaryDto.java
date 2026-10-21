package com.issuemanagement.dashboard.dto;

import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrioritizedIssueSummaryDto {
    private Long id;
    private String issueKey;
    private String title;
    private IssueType type;
    private IssuePriority priority;
    private IssueStatus status;
    private Long projectId;
    private String projectKey;
    private String projectName;
    private Long assigneeId;
    private String assigneeName;
    private Instant createdAt;
    private Instant updatedAt;

    public static PrioritizedIssueSummaryDto fromEntity(Issue issue) {
        return PrioritizedIssueSummaryDto.builder()
                .id(issue.getId())
                .issueKey(issue.getIssueKey())
                .title(issue.getTitle())
                .type(issue.getType())
                .priority(issue.getPriority())
                .status(issue.getStatus())
                .projectId(issue.getProject() != null ? issue.getProject().getId() : null)
                .projectKey(issue.getProject() != null ? issue.getProject().getProjectKey() : null)
                .projectName(issue.getProject() != null ? issue.getProject().getName() : null)
                .assigneeId(issue.getAssignee() != null ? issue.getAssignee().getId() : null)
                .assigneeName(issue.getAssignee() != null ? issue.getAssignee().getFullName() : null)
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .build();
    }
}

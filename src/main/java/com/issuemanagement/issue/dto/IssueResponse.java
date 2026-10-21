package com.issuemanagement.issue.dto;

import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueResponse {
    private Long id;
    private String issueKey;
    private String title;
    private String description;
    private IssueType type;
    private IssuePriority priority;
    private IssueStatus status;
    private Long projectId;
    private String projectKey;
    private String projectName;
    private Long reporterId;
    private String reporterName;
    private String reporterEmail;
    private Long assigneeId;
    private String assigneeName;
    private String assigneeEmail;
    private boolean archived;
    private Instant createdAt;
    private Instant updatedAt;

    public static IssueResponse fromEntity(Issue issue) {
        return IssueResponse.builder()
                .id(issue.getId())
                .issueKey(issue.getIssueKey())
                .title(issue.getTitle())
                .description(issue.getDescription())
                .type(issue.getType())
                .priority(issue.getPriority())
                .status(issue.getStatus())
                .projectId(issue.getProject().getId())
                .projectKey(issue.getProject().getProjectKey())
                .projectName(issue.getProject().getName())
                .reporterId(issue.getReporter().getId())
                .reporterName(issue.getReporter().getFirstName() + " " + issue.getReporter().getLastName())
                .reporterEmail(issue.getReporter().getEmail())
                .assigneeId(issue.getAssignee() != null ? issue.getAssignee().getId() : null)
                .assigneeName(issue.getAssignee() != null
                        ? issue.getAssignee().getFirstName() + " " + issue.getAssignee().getLastName()
                        : null)
                .assigneeEmail(issue.getAssignee() != null ? issue.getAssignee().getEmail() : null)
                .archived(issue.isArchived())
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .build();
    }
}

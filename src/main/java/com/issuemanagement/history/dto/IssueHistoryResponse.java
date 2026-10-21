package com.issuemanagement.history.dto;

import com.issuemanagement.history.entity.ChangeType;
import com.issuemanagement.history.entity.IssueHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueHistoryResponse {
    private Long id;
    private Long issueId;
    private Long actorId;
    private String actorName;
    private String actorEmail;
    private ChangeType changeType;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private Instant createdAt;

    public static IssueHistoryResponse fromEntity(IssueHistory history) {
        return IssueHistoryResponse.builder()
                .id(history.getId())
                .issueId(history.getIssue().getId())
                .actorId(history.getActor().getId())
                .actorName(history.getActor().getFirstName() + " " + history.getActor().getLastName())
                .actorEmail(history.getActor().getEmail())
                .changeType(history.getChangeType())
                .fieldName(history.getFieldName())
                .oldValue(history.getOldValue())
                .newValue(history.getNewValue())
                .createdAt(history.getCreatedAt())
                .build();
    }
}

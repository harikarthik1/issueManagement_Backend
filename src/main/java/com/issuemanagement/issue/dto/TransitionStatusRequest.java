package com.issuemanagement.issue.dto;

import com.issuemanagement.issue.entity.IssueStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitionStatusRequest {

    @NotNull(message = "Target status is required")
    private IssueStatus status;
}

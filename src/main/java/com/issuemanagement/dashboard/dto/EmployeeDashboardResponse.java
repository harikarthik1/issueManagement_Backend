package com.issuemanagement.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDashboardResponse {
    private Long employeeId;
    private String employeeName;
    private String employeeEmail;
    private long totalAssignedIssues;
    private long openCount;
    private long inProgressCount;
    private long resolvedCount;
    private long closedCount;
    private long highPriorityActiveCount;
    private long criticalPriorityActiveCount;
    private List<PrioritizedIssueSummaryDto> activeAssignedIssues;
}

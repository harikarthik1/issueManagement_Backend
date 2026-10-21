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
public class ProjectManagerDashboardResponse {
    private Long projectId;
    private String projectKey;
    private String projectName;
    private Long managerId;
    private String managerName;

    // Project status summary
    private long totalIssues;
    private long openCount;
    private long inProgressCount;
    private long resolvedCount;
    private long closedCount;
    private long pendingWorkCount; // openCount + inProgressCount
    private long highPriorityActiveCount;
    private long criticalPriorityActiveCount;
    private long unassignedActiveCount;

    // Workload breakdown for current active project members
    private List<ProjectMemberWorkloadDto> memberWorkloads;

    // Prioritized list of unassigned active issues
    private List<PrioritizedIssueSummaryDto> unassignedActiveIssues;
}

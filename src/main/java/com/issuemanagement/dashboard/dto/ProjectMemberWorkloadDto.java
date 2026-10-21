package com.issuemanagement.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberWorkloadDto {
    private Long employeeId;
    private String employeeName;
    private String employeeEmail;
    private long openCount;
    private long inProgressCount;
    private long resolvedCount;
    private long closedCount;
    private long totalAssignedCount;
    private long highPriorityActiveCount;
    private long criticalPriorityActiveCount;
}

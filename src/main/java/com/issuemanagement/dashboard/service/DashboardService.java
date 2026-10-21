package com.issuemanagement.dashboard.service;

import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.dashboard.dto.EmployeeDashboardResponse;
import com.issuemanagement.dashboard.dto.PrioritizedIssueSummaryDto;
import com.issuemanagement.dashboard.dto.ProjectManagerDashboardResponse;
import com.issuemanagement.dashboard.dto.ProjectMemberWorkloadDto;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.security.SecurityUtils;
import com.issuemanagement.identity.service.UserService;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.repository.IssueRepository;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.entity.ProjectMember;
import com.issuemanagement.project.repository.ProjectMemberRepository;
import com.issuemanagement.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IssueRepository issueRepository;
    private final UserService userService;
    private final ProjectService projectService;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public EmployeeDashboardResponse getEmployeeDashboard() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User employee = userService.getActiveUserEntity(currentUserId);

        // 1. Status counts via aggregation query
        List<Object[]> statusRows = issueRepository.countEmployeeAssignedIssuesByStatus(currentUserId);
        long openCount = 0;
        long inProgressCount = 0;
        long resolvedCount = 0;
        long closedCount = 0;

        for (Object[] row : statusRows) {
            IssueStatus status = (IssueStatus) row[0];
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case OPEN -> openCount = count;
                case IN_PROGRESS -> inProgressCount = count;
                case RESOLVED -> resolvedCount = count;
                case CLOSED -> closedCount = count;
            }
        }
        long totalAssigned = openCount + inProgressCount + resolvedCount + closedCount;

        // 2. Active priority counts via aggregation query
        List<Object[]> priorityRows = issueRepository.countEmployeeActiveAssignedIssuesByPriority(currentUserId);
        long highPriorityActiveCount = 0;
        long criticalPriorityActiveCount = 0;

        for (Object[] row : priorityRows) {
            IssuePriority priority = (IssuePriority) row[0];
            long count = ((Number) row[1]).longValue();
            if (priority == IssuePriority.HIGH) {
                highPriorityActiveCount = count;
            } else if (priority == IssuePriority.CRITICAL) {
                criticalPriorityActiveCount = count;
            }
        }

        // 3. Prioritized active issues list
        List<Issue> activeIssues = issueRepository.findEmployeeActiveAssignedIssuesPrioritized(currentUserId);
        List<PrioritizedIssueSummaryDto> activeIssueDtos = activeIssues.stream()
                .map(PrioritizedIssueSummaryDto::fromEntity)
                .toList();

        return EmployeeDashboardResponse.builder()
                .employeeId(employee.getId())
                .employeeName(employee.getFullName())
                .employeeEmail(employee.getEmail())
                .totalAssignedIssues(totalAssigned)
                .openCount(openCount)
                .inProgressCount(inProgressCount)
                .resolvedCount(resolvedCount)
                .closedCount(closedCount)
                .highPriorityActiveCount(highPriorityActiveCount)
                .criticalPriorityActiveCount(criticalPriorityActiveCount)
                .activeAssignedIssues(activeIssueDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public ProjectManagerDashboardResponse getProjectManagerDashboard(Long projectId) {
        Project project = projectService.getProjectEntity(projectId);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Role currentUserRole = SecurityUtils.getCurrentUserRole();

        boolean isAdmin = currentUserRole == Role.ADMIN;
        boolean isAssignedPm = currentUserRole == Role.PROJECT_MANAGER
                && project.getManager() != null
                && project.getManager().getId().equals(currentUserId);

        if (!isAdmin && !isAssignedPm) {
            throw new ForbiddenException("Only an Admin or the assigned Project Manager can view the project dashboard");
        }

        // 1. Project-wide status counts
        List<Object[]> statusRows = issueRepository.countProjectIssuesByStatus(projectId);
        long openCount = 0;
        long inProgressCount = 0;
        long resolvedCount = 0;
        long closedCount = 0;

        for (Object[] row : statusRows) {
            IssueStatus status = (IssueStatus) row[0];
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case OPEN -> openCount = count;
                case IN_PROGRESS -> inProgressCount = count;
                case RESOLVED -> resolvedCount = count;
                case CLOSED -> closedCount = count;
            }
        }
        long totalIssues = openCount + inProgressCount + resolvedCount + closedCount;
        long pendingWorkCount = openCount + inProgressCount;

        // 2. Project-wide active priority counts
        List<Object[]> priorityRows = issueRepository.countProjectActiveIssuesByPriority(projectId);
        long highPriorityActiveCount = 0;
        long criticalPriorityActiveCount = 0;

        for (Object[] row : priorityRows) {
            IssuePriority priority = (IssuePriority) row[0];
            long count = ((Number) row[1]).longValue();
            if (priority == IssuePriority.HIGH) {
                highPriorityActiveCount = count;
            } else if (priority == IssuePriority.CRITICAL) {
                criticalPriorityActiveCount = count;
            }
        }

        // 3. Unassigned active issues
        long unassignedActiveCount = issueRepository.countProjectUnassignedActiveIssues(projectId);
        List<Issue> unassignedIssues = issueRepository.findProjectUnassignedActiveIssuesPrioritized(projectId);
        List<PrioritizedIssueSummaryDto> unassignedDtos = unassignedIssues.stream()
                .map(PrioritizedIssueSummaryDto::fromEntity)
                .toList();

        // 4. Employee workload aggregation
        List<Object[]> memberStatusRows = issueRepository.countProjectMemberIssuesByStatus(projectId);
        Map<Long, Map<IssueStatus, Long>> memberStatusMap = new HashMap<>();
        for (Object[] row : memberStatusRows) {
            Long memberId = (Long) row[0];
            IssueStatus status = (IssueStatus) row[1];
            long count = ((Number) row[2]).longValue();
            memberStatusMap.computeIfAbsent(memberId, k -> new HashMap<>()).put(status, count);
        }

        List<Object[]> memberPriorityRows = issueRepository.countProjectMemberActiveIssuesByPriority(projectId);
        Map<Long, Map<IssuePriority, Long>> memberPriorityMap = new HashMap<>();
        for (Object[] row : memberPriorityRows) {
            Long memberId = (Long) row[0];
            IssuePriority priority = (IssuePriority) row[1];
            long count = ((Number) row[2]).longValue();
            memberPriorityMap.computeIfAbsent(memberId, k -> new HashMap<>()).put(priority, count);
        }

        List<ProjectMember> currentMembers = projectMemberRepository.findByProjectIdWithUser(projectId);
        List<ProjectMemberWorkloadDto> memberWorkloads = new ArrayList<>();

        for (ProjectMember member : currentMembers) {
            User user = member.getUser();
            // Inactive employees must not appear as current active team members
            if (!user.isActive()) {
                continue;
            }

            Long uId = user.getId();
            Map<IssueStatus, Long> statusMap = memberStatusMap.getOrDefault(uId, Collections.emptyMap());
            Map<IssuePriority, Long> prioMap = memberPriorityMap.getOrDefault(uId, Collections.emptyMap());

            long mOpen = statusMap.getOrDefault(IssueStatus.OPEN, 0L);
            long mInProgress = statusMap.getOrDefault(IssueStatus.IN_PROGRESS, 0L);
            long mResolved = statusMap.getOrDefault(IssueStatus.RESOLVED, 0L);
            long mClosed = statusMap.getOrDefault(IssueStatus.CLOSED, 0L);
            long mTotal = mOpen + mInProgress + mResolved + mClosed;

            long mHigh = prioMap.getOrDefault(IssuePriority.HIGH, 0L);
            long mCrit = prioMap.getOrDefault(IssuePriority.CRITICAL, 0L);

            memberWorkloads.add(ProjectMemberWorkloadDto.builder()
                    .employeeId(uId)
                    .employeeName(user.getFullName())
                    .employeeEmail(user.getEmail())
                    .openCount(mOpen)
                    .inProgressCount(mInProgress)
                    .resolvedCount(mResolved)
                    .closedCount(mClosed)
                    .totalAssignedCount(mTotal)
                    .highPriorityActiveCount(mHigh)
                    .criticalPriorityActiveCount(mCrit)
                    .build());
        }

        return ProjectManagerDashboardResponse.builder()
                .projectId(project.getId())
                .projectKey(project.getProjectKey())
                .projectName(project.getName())
                .managerId(project.getManager() != null ? project.getManager().getId() : null)
                .managerName(project.getManager() != null ? project.getManager().getFullName() : null)
                .totalIssues(totalIssues)
                .openCount(openCount)
                .inProgressCount(inProgressCount)
                .resolvedCount(resolvedCount)
                .closedCount(closedCount)
                .pendingWorkCount(pendingWorkCount)
                .highPriorityActiveCount(highPriorityActiveCount)
                .criticalPriorityActiveCount(criticalPriorityActiveCount)
                .unassignedActiveCount(unassignedActiveCount)
                .memberWorkloads(memberWorkloads)
                .unassignedActiveIssues(unassignedDtos)
                .build();
    }
}

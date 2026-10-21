package com.issuemanagement.dashboard.service;

import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.dashboard.dto.EmployeeDashboardResponse;
import com.issuemanagement.dashboard.dto.ProjectManagerDashboardResponse;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.security.UserPrincipal;
import com.issuemanagement.identity.service.UserService;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.issue.repository.IssueRepository;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.entity.ProjectMember;
import com.issuemanagement.project.entity.ProjectStatus;
import com.issuemanagement.project.repository.ProjectMemberRepository;
import com.issuemanagement.project.service.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private UserService userService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private User admin;
    private User pmAlpha;
    private User pmBeta;
    private User employee1;
    private User employee2;
    private User inactiveEmployee;
    private Project projectAlpha;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(1L).email("admin@test.com").role(Role.ADMIN).active(true).build();
        pmAlpha = User.builder().id(2L).email("pm.alpha@test.com").role(Role.PROJECT_MANAGER).active(true).build();
        pmBeta = User.builder().id(3L).email("pm.beta@test.com").role(Role.PROJECT_MANAGER).active(true).build();
        employee1 = User.builder().id(4L).email("emp1@test.com").firstName("John").lastName("Doe").role(Role.EMPLOYEE).active(true).build();
        employee2 = User.builder().id(5L).email("emp2@test.com").firstName("Jane").lastName("Smith").role(Role.EMPLOYEE).active(true).build();
        inactiveEmployee = User.builder().id(6L).email("inactive@test.com").firstName("Old").lastName("Member").role(Role.EMPLOYEE).active(false).build();

        projectAlpha = Project.builder()
                .id(10L)
                .projectKey("ALPHA")
                .name("Project Alpha")
                .status(ProjectStatus.ACTIVE)
                .manager(pmAlpha)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Employee dashboard returns correct status counts, active priority counts, and prioritized issues")
    void testGetEmployeeDashboard_Success() {
        authenticate(employee1);
        when(userService.getActiveUserEntity(4L)).thenReturn(employee1);

        // Status counts: 2 OPEN, 1 IN_PROGRESS, 1 RESOLVED, 1 CLOSED
        List<Object[]> statusRows = List.of(
                new Object[]{IssueStatus.OPEN, 2L},
                new Object[]{IssueStatus.IN_PROGRESS, 1L},
                new Object[]{IssueStatus.RESOLVED, 1L},
                new Object[]{IssueStatus.CLOSED, 1L}
        );
        when(issueRepository.countEmployeeAssignedIssuesByStatus(4L)).thenReturn(statusRows);

        // Priority counts for active: 1 CRITICAL, 1 HIGH
        List<Object[]> priorityRows = List.of(
                new Object[]{IssuePriority.CRITICAL, 1L},
                new Object[]{IssuePriority.HIGH, 1L},
                new Object[]{IssuePriority.MEDIUM, 1L}
        );
        when(issueRepository.countEmployeeActiveAssignedIssuesByPriority(4L)).thenReturn(priorityRows);

        Issue critIssue = Issue.builder().id(101L).issueKey("ALPHA-1").title("Critical Bug").type(IssueType.BUG).priority(IssuePriority.CRITICAL).status(IssueStatus.OPEN).project(projectAlpha).assignee(employee1).build();
        Issue highIssue = Issue.builder().id(102L).issueKey("ALPHA-2").title("High Feature").type(IssueType.FEATURE).priority(IssuePriority.HIGH).status(IssueStatus.IN_PROGRESS).project(projectAlpha).assignee(employee1).build();
        Issue medIssue = Issue.builder().id(103L).issueKey("ALPHA-3").title("Medium Task").type(IssueType.TASK).priority(IssuePriority.MEDIUM).status(IssueStatus.OPEN).project(projectAlpha).assignee(employee1).build();

        when(issueRepository.findEmployeeActiveAssignedIssuesPrioritized(4L))
                .thenReturn(List.of(critIssue, highIssue, medIssue));

        EmployeeDashboardResponse response = dashboardService.getEmployeeDashboard();

        assertThat(response.getEmployeeId()).isEqualTo(4L);
        assertThat(response.getTotalAssignedIssues()).isEqualTo(5L);
        assertThat(response.getOpenCount()).isEqualTo(2L);
        assertThat(response.getInProgressCount()).isEqualTo(1L);
        assertThat(response.getResolvedCount()).isEqualTo(1L);
        assertThat(response.getClosedCount()).isEqualTo(1L);
        assertThat(response.getCriticalPriorityActiveCount()).isEqualTo(1L);
        assertThat(response.getHighPriorityActiveCount()).isEqualTo(1L);
        assertThat(response.getActiveAssignedIssues()).hasSize(3);
        assertThat(response.getActiveAssignedIssues().get(0).getPriority()).isEqualTo(IssuePriority.CRITICAL);
        assertThat(response.getActiveAssignedIssues().get(1).getPriority()).isEqualTo(IssuePriority.HIGH);
        assertThat(response.getActiveAssignedIssues().get(2).getPriority()).isEqualTo(IssuePriority.MEDIUM);
    }

    @Test
    @DisplayName("Employee dashboard returns valid empty state when employee has no assigned issues")
    void testGetEmployeeDashboard_Empty() {
        authenticate(employee1);
        when(userService.getActiveUserEntity(4L)).thenReturn(employee1);
        when(issueRepository.countEmployeeAssignedIssuesByStatus(4L)).thenReturn(Collections.emptyList());
        when(issueRepository.countEmployeeActiveAssignedIssuesByPriority(4L)).thenReturn(Collections.emptyList());
        when(issueRepository.findEmployeeActiveAssignedIssuesPrioritized(4L)).thenReturn(Collections.emptyList());

        EmployeeDashboardResponse response = dashboardService.getEmployeeDashboard();

        assertThat(response.getTotalAssignedIssues()).isZero();
        assertThat(response.getOpenCount()).isZero();
        assertThat(response.getInProgressCount()).isZero();
        assertThat(response.getResolvedCount()).isZero();
        assertThat(response.getClosedCount()).isZero();
        assertThat(response.getHighPriorityActiveCount()).isZero();
        assertThat(response.getCriticalPriorityActiveCount()).isZero();
        assertThat(response.getActiveAssignedIssues()).isEmpty();
    }

    @Test
    @DisplayName("Project Manager dashboard calculates reconciliation, workloads, and unassigned issues")
    void testGetProjectManagerDashboard_Success_AssignedPm() {
        authenticate(pmAlpha);
        when(projectService.getProjectEntity(10L)).thenReturn(projectAlpha);

        // Project status counts: 3 OPEN, 2 IN_PROGRESS, 1 RESOLVED, 2 CLOSED = 8 total
        List<Object[]> statusRows = List.of(
                new Object[]{IssueStatus.OPEN, 3L},
                new Object[]{IssueStatus.IN_PROGRESS, 2L},
                new Object[]{IssueStatus.RESOLVED, 1L},
                new Object[]{IssueStatus.CLOSED, 2L}
        );
        when(issueRepository.countProjectIssuesByStatus(10L)).thenReturn(statusRows);

        // Project active priority counts: 2 CRITICAL, 1 HIGH, 2 MEDIUM
        List<Object[]> priorityRows = List.of(
                new Object[]{IssuePriority.CRITICAL, 2L},
                new Object[]{IssuePriority.HIGH, 1L},
                new Object[]{IssuePriority.MEDIUM, 2L}
        );
        when(issueRepository.countProjectActiveIssuesByPriority(10L)).thenReturn(priorityRows);

        // Unassigned active count: 1
        when(issueRepository.countProjectUnassignedActiveIssues(10L)).thenReturn(1L);
        Issue unassignedIssue = Issue.builder().id(105L).issueKey("ALPHA-5").title("Unassigned task").type(IssueType.TASK).priority(IssuePriority.HIGH).status(IssueStatus.OPEN).project(projectAlpha).assignee(null).build();
        when(issueRepository.findProjectUnassignedActiveIssuesPrioritized(10L)).thenReturn(List.of(unassignedIssue));

        // Member status rows:
        // emp1: 2 OPEN, 1 IN_PROGRESS, 1 RESOLVED = 4 total
        // emp2 has 0 issues assigned
        List<Object[]> memberStatusRows = List.of(
                new Object[]{4L, IssueStatus.OPEN, 2L},
                new Object[]{4L, IssueStatus.IN_PROGRESS, 1L},
                new Object[]{4L, IssueStatus.RESOLVED, 1L}
        );
        when(issueRepository.countProjectMemberIssuesByStatus(10L)).thenReturn(memberStatusRows);

        List<Object[]> memberPriorityRows = List.of(
                new Object[]{4L, IssuePriority.CRITICAL, 1L},
                new Object[]{4L, IssuePriority.HIGH, 1L}
        );
        when(issueRepository.countProjectMemberActiveIssuesByPriority(10L)).thenReturn(memberPriorityRows);

        // Project members: employee1 (active), employee2 (active), inactiveEmployee (inactive - should be excluded)
        ProjectMember m1 = ProjectMember.builder().id(1L).project(projectAlpha).user(employee1).build();
        ProjectMember m2 = ProjectMember.builder().id(2L).project(projectAlpha).user(employee2).build();
        ProjectMember m3 = ProjectMember.builder().id(3L).project(projectAlpha).user(inactiveEmployee).build();
        when(projectMemberRepository.findByProjectIdWithUser(10L)).thenReturn(List.of(m1, m2, m3));

        ProjectManagerDashboardResponse response = dashboardService.getProjectManagerDashboard(10L);

        assertThat(response.getProjectId()).isEqualTo(10L);
        assertThat(response.getProjectKey()).isEqualTo("ALPHA");
        assertThat(response.getTotalIssues()).isEqualTo(8L);
        assertThat(response.getOpenCount()).isEqualTo(3L);
        assertThat(response.getInProgressCount()).isEqualTo(2L);
        assertThat(response.getResolvedCount()).isEqualTo(1L);
        assertThat(response.getClosedCount()).isEqualTo(2L);
        assertThat(response.getPendingWorkCount()).isEqualTo(5L); // 3 OPEN + 2 IN_PROGRESS
        assertThat(response.getCriticalPriorityActiveCount()).isEqualTo(2L);
        assertThat(response.getHighPriorityActiveCount()).isEqualTo(1L);
        assertThat(response.getUnassignedActiveCount()).isEqualTo(1L);
        assertThat(response.getUnassignedActiveIssues()).hasSize(1);

        // Member workloads: employee1 and employee2 (inactiveEmployee excluded)
        assertThat(response.getMemberWorkloads()).hasSize(2);

        // emp1 workload
        var emp1Workload = response.getMemberWorkloads().stream()
                .filter(w -> w.getEmployeeId().equals(4L)).findFirst().orElseThrow();
        assertThat(emp1Workload.getTotalAssignedCount()).isEqualTo(4L);
        assertThat(emp1Workload.getOpenCount()).isEqualTo(2L);
        assertThat(emp1Workload.getInProgressCount()).isEqualTo(1L);
        assertThat(emp1Workload.getResolvedCount()).isEqualTo(1L);
        assertThat(emp1Workload.getClosedCount()).isZero();
        assertThat(emp1Workload.getCriticalPriorityActiveCount()).isEqualTo(1L);
        assertThat(emp1Workload.getHighPriorityActiveCount()).isEqualTo(1L);

        // emp2 workload (0 issues assigned, but included in active member list)
        var emp2Workload = response.getMemberWorkloads().stream()
                .filter(w -> w.getEmployeeId().equals(5L)).findFirst().orElseThrow();
        assertThat(emp2Workload.getTotalAssignedCount()).isZero();
        assertThat(emp2Workload.getOpenCount()).isZero();
    }

    @Test
    @DisplayName("Project Manager dashboard throws ForbiddenException when unrelated PM attempts access")
    void testGetProjectManagerDashboard_ThrowsForbidden_WhenUnrelatedPm() {
        authenticate(pmBeta);
        when(projectService.getProjectEntity(10L)).thenReturn(projectAlpha);

        assertThatThrownBy(() -> dashboardService.getProjectManagerDashboard(10L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only an Admin or the assigned Project Manager can view the project dashboard");
    }

    @Test
    @DisplayName("Project Manager dashboard throws ForbiddenException when ordinary employee attempts access")
    void testGetProjectManagerDashboard_ThrowsForbidden_WhenEmployee() {
        authenticate(employee1);
        when(projectService.getProjectEntity(10L)).thenReturn(projectAlpha);

        assertThatThrownBy(() -> dashboardService.getProjectManagerDashboard(10L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only an Admin or the assigned Project Manager can view the project dashboard");
    }

    @Test
    @DisplayName("Project Manager dashboard succeeds when Admin views any project")
    void testGetProjectManagerDashboard_Success_WhenAdmin() {
        authenticate(admin);
        when(projectService.getProjectEntity(10L)).thenReturn(projectAlpha);
        when(issueRepository.countProjectIssuesByStatus(10L)).thenReturn(Collections.emptyList());
        when(issueRepository.countProjectActiveIssuesByPriority(10L)).thenReturn(Collections.emptyList());
        when(issueRepository.countProjectUnassignedActiveIssues(10L)).thenReturn(0L);
        when(issueRepository.findProjectUnassignedActiveIssuesPrioritized(10L)).thenReturn(Collections.emptyList());
        when(issueRepository.countProjectMemberIssuesByStatus(10L)).thenReturn(Collections.emptyList());
        when(issueRepository.countProjectMemberActiveIssuesByPriority(10L)).thenReturn(Collections.emptyList());
        when(projectMemberRepository.findByProjectIdWithUser(10L)).thenReturn(Collections.emptyList());

        ProjectManagerDashboardResponse response = dashboardService.getProjectManagerDashboard(10L);

        assertThat(response.getProjectId()).isEqualTo(10L);
        assertThat(response.getTotalIssues()).isZero();
    }
}

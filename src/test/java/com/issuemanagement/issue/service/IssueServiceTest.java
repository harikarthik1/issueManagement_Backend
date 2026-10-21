package com.issuemanagement.issue.service;

import com.issuemanagement.audit.service.AuditService;
import com.issuemanagement.common.exception.BadRequestException;
import com.issuemanagement.history.entity.ChangeType;
import com.issuemanagement.history.service.IssueHistoryService;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.security.UserPrincipal;
import com.issuemanagement.identity.service.UserService;
import com.issuemanagement.issue.dto.CreateIssueRequest;
import com.issuemanagement.issue.dto.IssueResponse;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.issue.repository.IssueRepository;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.entity.ProjectStatus;
import com.issuemanagement.project.service.ProjectAuthorizationService;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private ProjectAuthorizationService projectAuthService;

    @Mock
    private UserService userService;

    @Mock
    private IssueHistoryService historyService;

    @Mock
    private AuditService auditService;

    @Mock
    private IssueVectorService issueVectorService;

    @InjectMocks
    private IssueService issueService;

    private User reporter;
    private User employeeMember;
    private User manager;
    private Project project;
    private Issue issue;

    @BeforeEach
    void setUp() {
        reporter = User.builder()
                .id(1L)
                .email("admin@test.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .active(true)
                .build();

        employeeMember = User.builder()
                .id(3L)
                .email("dev@test.com")
                .firstName("Dev")
                .lastName("User")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        manager = User.builder()
                .id(2L)
                .email("pm@test.com")
                .firstName("Project")
                .lastName("Manager")
                .role(Role.PROJECT_MANAGER)
                .active(true)
                .build();

        project = Project.builder()
                .id(1L)
                .name("Core Platform")
                .projectKey("CORE")
                .manager(manager)
                .status(ProjectStatus.ACTIVE)
                .build();

        issue = Issue.builder()
                .id(100L)
                .issueKey("CORE-1")
                .title("Fix bug")
                .type(IssueType.BUG)
                .priority(IssuePriority.HIGH)
                .status(IssueStatus.OPEN)
                .project(project)
                .reporter(reporter)
                .assignee(employeeMember)
                .archived(false)
                .build();

        UserPrincipal principal = UserPrincipal.create(reporter);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Create issue successfully and record history")
    void testCreateIssueSuccess() {
        CreateIssueRequest request = CreateIssueRequest.builder()
                .title("Database connection issue")
                .description("Detail description")
                .type(IssueType.BUG)
                .priority(IssuePriority.HIGH)
                .assigneeId(3L)
                .build();

        when(projectService.getActiveProjectEntity(1L)).thenReturn(project);
        when(projectService.getProjectEntity(1L)).thenReturn(project);
        when(userService.getActiveUserEntity(1L)).thenReturn(reporter);
        when(userService.getEligibleAssigneeEntity(3L)).thenReturn(employeeMember);
        when(projectAuthService.isProjectMember(1L, 3L)).thenReturn(true);
        when(issueRepository.countByProjectId(1L)).thenReturn(0L);
        when(issueRepository.save(any(Issue.class))).thenReturn(issue);

        IssueResponse response = issueService.createIssue(1L, request);

        assertNotNull(response);
        assertEquals("CORE-1", response.getIssueKey());
        verify(historyService).recordHistory(any(), eq(reporter), eq(ChangeType.CREATED), eq("status"), isNull(), eq("OPEN"));
        verify(auditService).logCurrentActorEvent(eq("CREATE_ISSUE"), eq("Issue"), any(), any());
    }

    @Test
    @DisplayName("Create issue succeeds when assignee is the project manager")
    void testCreateIssueSuccessWithProjectManagerAssignee() {
        CreateIssueRequest request = CreateIssueRequest.builder()
                .title("PM assigned task")
                .description("Detail description")
                .type(IssueType.TASK)
                .priority(IssuePriority.MEDIUM)
                .assigneeId(2L)
                .build();

        when(projectService.getActiveProjectEntity(1L)).thenReturn(project);
        when(projectService.getProjectEntity(1L)).thenReturn(project);
        when(userService.getActiveUserEntity(1L)).thenReturn(reporter);
        when(userService.getEligibleAssigneeEntity(2L)).thenReturn(manager);
        when(issueRepository.countByProjectId(1L)).thenReturn(0L);
        when(issueRepository.save(any(Issue.class))).thenReturn(issue);

        IssueResponse response = issueService.createIssue(1L, request);

        assertNotNull(response);
        verify(issueRepository).save(any(Issue.class));
    }

    @Test
    @DisplayName("Create issue fails if assignee is not a member of the project")
    void testCreateIssueAssigneeNotProjectMember() {
        CreateIssueRequest request = CreateIssueRequest.builder()
                .title("Bug outside team")
                .type(IssueType.BUG)
                .priority(IssuePriority.MEDIUM)
                .assigneeId(3L)
                .build();

        when(projectService.getActiveProjectEntity(1L)).thenReturn(project);
        when(projectService.getProjectEntity(1L)).thenReturn(project);
        when(userService.getActiveUserEntity(1L)).thenReturn(reporter);
        when(userService.getEligibleAssigneeEntity(3L)).thenReturn(employeeMember);
        when(projectAuthService.isProjectMember(1L, 3L)).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> issueService.createIssue(1L, request));
        assertTrue(ex.getMessage().contains("neither the assigned project manager nor a member"));
        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valid status transition updates status and writes history")
    void testValidStatusTransition() {
        when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));
        when(userService.getActiveUserEntity(1L)).thenReturn(reporter);
        when(issueRepository.save(any(Issue.class))).thenReturn(issue);

        IssueResponse response = issueService.transitionStatus(100L, IssueStatus.IN_PROGRESS);

        assertNotNull(response);
        assertEquals(IssueStatus.IN_PROGRESS, issue.getStatus());
        verify(historyService).recordHistory(eq(issue), eq(reporter), eq(ChangeType.STATUS_CHANGED), eq("status"), eq("OPEN"), eq("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Invalid status transition throws BadRequestException")
    void testInvalidStatusTransitionRejected() {
        when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));
        when(userService.getActiveUserEntity(1L)).thenReturn(reporter);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> issueService.transitionStatus(100L, IssueStatus.RESOLVED));

        assertTrue(ex.getMessage().contains("Invalid status transition"));
        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Archive issue sets archived to true and logs history")
    void testArchiveIssue() {
        when(issueRepository.findById(100L)).thenReturn(Optional.of(issue));
        when(userService.getActiveUserEntity(1L)).thenReturn(reporter);

        issueService.archiveIssue(100L);

        assertTrue(issue.isArchived());
        verify(issueRepository).save(issue);
        verify(historyService).recordHistory(eq(issue), eq(reporter), eq(ChangeType.ARCHIVED), eq("archived"), eq("false"), eq("true"));
    }
}

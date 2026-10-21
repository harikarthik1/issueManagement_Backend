package com.issuemanagement.project.service;

import com.issuemanagement.audit.service.AuditService;
import com.issuemanagement.common.exception.BadRequestException;
import com.issuemanagement.common.exception.ConflictException;
import com.issuemanagement.common.exception.ResourceNotFoundException;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.service.UserService;
import com.issuemanagement.project.dto.CreateProjectRequest;
import com.issuemanagement.project.dto.ProjectMemberResponse;
import com.issuemanagement.project.dto.ProjectResponse;
import com.issuemanagement.project.dto.UpdateProjectRequest;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.entity.ProjectMember;
import com.issuemanagement.project.entity.ProjectStatus;
import com.issuemanagement.project.repository.ProjectMemberRepository;
import com.issuemanagement.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private ProjectAuthorizationService projectAuthService;

    @Mock
    private AuditService auditService;

    @Mock
    private com.issuemanagement.issue.repository.IssueRepository issueRepository;

    @Mock
    private com.issuemanagement.history.service.IssueHistoryService historyService;

    @InjectMocks
    private ProjectService projectService;

    private User manager;
    private User employee;
    private Project project;

    @BeforeEach
    void setUp() {
        manager = User.builder()
                .id(2L)
                .email("pm@test.com")
                .firstName("Project")
                .lastName("Manager")
                .role(Role.PROJECT_MANAGER)
                .active(true)
                .build();

        employee = User.builder()
                .id(3L)
                .email("dev@test.com")
                .firstName("Dev")
                .lastName("User")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        project = Project.builder()
                .id(1L)
                .name("Core System")
                .projectKey("CORE")
                .description("Core Platform")
                .status(ProjectStatus.ACTIVE)
                .manager(manager)
                .build();
    }

    @Test
    @DisplayName("Create project successfully with unique key and active PM")
    void testCreateProjectSuccess() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("Core System")
                .projectKey("CORE")
                .description("Core Platform")
                .managerId(2L)
                .build();

        when(projectRepository.existsByProjectKey("CORE")).thenReturn(false);
        when(userService.getEligibleProjectManagerEntity(2L)).thenReturn(manager);
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectService.createProject(request);

        assertNotNull(response);
        assertEquals("CORE", response.getProjectKey());
        assertEquals("Core System", response.getName());
        assertEquals(2L, response.getManagerId());
        verify(auditService).logCurrentActorEvent(eq("CREATE_PROJECT"), eq("Project"), any(), any());
    }

    @Test
    @DisplayName("Create project throws ConflictException if project key already exists")
    void testCreateProjectDuplicateKey() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("Duplicate System")
                .projectKey("CORE")
                .managerId(2L)
                .build();

        when(projectRepository.existsByProjectKey("CORE")).thenReturn(true);

        assertThrows(ConflictException.class, () -> projectService.createProject(request));
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("Add member successfully to active project")
    void testAddMemberSuccess() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(1L, 3L)).thenReturn(false);
        when(userService.getEligibleEmployeeEntity(3L)).thenReturn(employee);

        ProjectMember member = ProjectMember.builder()
                .id(10L)
                .project(project)
                .user(employee)
                .build();
        when(projectMemberRepository.save(any(ProjectMember.class))).thenReturn(member);

        ProjectMemberResponse response = projectService.addProjectMember(1L, 3L);

        assertNotNull(response);
        assertEquals(3L, response.getUserId());
        assertEquals(1L, response.getProjectId());
        verify(projectAuthService).checkMembershipManagementPermission(project);
    }

    @Test
    @DisplayName("Add member throws ConflictException if user is already a member")
    void testAddMemberAlreadyExists() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(1L, 3L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> projectService.addProjectMember(1L, 3L));
        verify(projectMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Add member throws BadRequestException if attempting to add assigned PM as member")
    void testAddMemberAssignedManagerRejected() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThrows(BadRequestException.class, () -> projectService.addProjectMember(1L, 2L));
        verify(projectMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Add member throws BadRequestException if project is archived")
    void testAddMemberArchivedProject() {
        project.setStatus(ProjectStatus.ARCHIVED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThrows(BadRequestException.class, () -> projectService.addProjectMember(1L, 3L));
    }

    @Test
    @DisplayName("Remove member successfully from active project")
    void testRemoveMemberSuccess() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectMember member = ProjectMember.builder()
                .id(10L)
                .project(project)
                .user(employee)
                .build();
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 3L)).thenReturn(Optional.of(member));
        when(issueRepository.findByProjectIdAndAssigneeIdAndStatusIn(eq(1L), eq(3L), any())).thenReturn(java.util.Collections.emptyList());

        projectService.removeProjectMember(1L, 3L);

        verify(projectMemberRepository).delete(member);
        verify(auditService).logCurrentActorEvent(eq("REMOVE_PROJECT_MEMBER"), eq("ProjectMember"), any(), any());
    }

    @Test
    @DisplayName("Remove member unassigns all assigned issues in project and logs history")
    void testRemoveMemberUnassignsIssues() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        com.issuemanagement.identity.security.UserPrincipal.create(manager), null, java.util.Collections.emptyList()
                )
        );

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectMember member = ProjectMember.builder()
                .id(10L)
                .project(project)
                .user(employee)
                .build();
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 3L)).thenReturn(Optional.of(member));
        when(userService.getUserEntity(2L)).thenReturn(manager);

        com.issuemanagement.issue.entity.Issue assignedIssue = com.issuemanagement.issue.entity.Issue.builder()
                .id(100L)
                .project(project)
                .assignee(employee)
                .build();
        when(issueRepository.findByProjectIdAndAssigneeIdAndStatusIn(eq(1L), eq(3L), any()))
                .thenReturn(java.util.List.of(assignedIssue));

        projectService.removeProjectMember(1L, 3L);

        assertNull(assignedIssue.getAssignee());
        verify(issueRepository).save(assignedIssue);
        verify(historyService).recordHistory(eq(assignedIssue), eq(manager), eq(com.issuemanagement.history.entity.ChangeType.ASSIGNEE_CHANGED), eq("assignee"), eq("dev@test.com"), any());
        verify(projectMemberRepository).delete(member);

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Remove member throws BadRequestException if attempting to remove assigned PM")
    void testRemoveMemberAssignedManagerRejected() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThrows(BadRequestException.class, () -> projectService.removeProjectMember(1L, 2L));
        verify(projectMemberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Remove member throws ResourceNotFoundException if user is not in project")
    void testRemoveMemberNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.removeProjectMember(1L, 99L));
    }

    @Test
    @DisplayName("Archive project changes status and logs audit event")
    void testArchiveProject() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectService.archiveProject(1L);

        assertEquals(ProjectStatus.ARCHIVED, response.getStatus());
        verify(auditService).logCurrentActorEvent(eq("ARCHIVE_PROJECT"), eq("Project"), any(), any());
    }

    @Test
    @DisplayName("Archive project throws BadRequestException if already archived")
    void testArchiveProjectAlreadyArchived() {
        project.setStatus(ProjectStatus.ARCHIVED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThrows(BadRequestException.class, () -> projectService.archiveProject(1L));
    }

    @Test
    @DisplayName("Update project throws BadRequestException if project is archived")
    void testUpdateArchivedProjectRejected() {
        project.setStatus(ProjectStatus.ARCHIVED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .name("New Name")
                .description("New Desc")
                .build();

        assertThrows(BadRequestException.class, () -> projectService.updateProject(1L, request));
    }
}

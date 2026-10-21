package com.issuemanagement.project.service;

import com.issuemanagement.audit.service.AuditService;
import com.issuemanagement.common.dto.PageResponse;
import com.issuemanagement.common.exception.BadRequestException;
import com.issuemanagement.common.exception.ConflictException;
import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.common.exception.ResourceNotFoundException;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.security.SecurityUtils;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;
    private final ProjectAuthorizationService projectAuthService;
    private final AuditService auditService;
    private final com.issuemanagement.issue.repository.IssueRepository issueRepository;
    private final com.issuemanagement.history.service.IssueHistoryService historyService;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse createProject(CreateProjectRequest request) {
        String projectKey = request.getProjectKey().trim().toUpperCase();
        if (projectRepository.existsByProjectKey(projectKey)) {
            throw new ConflictException("Project key already exists: " + projectKey);
        }

        User manager = userService.getEligibleProjectManagerEntity(request.getManagerId());

        Project project = Project.builder()
                .name(request.getName().trim())
                .projectKey(projectKey)
                .description(request.getDescription())
                .status(ProjectStatus.ACTIVE)
                .manager(manager)
                .build();

        Project savedProject = projectRepository.save(project);
        auditService.logCurrentActorEvent(
                "CREATE_PROJECT",
                "Project",
                savedProject.getId().toString(),
                "Created project " + savedProject.getProjectKey() + " with manager " + manager.getEmail()
        );

        return ProjectResponse.fromEntity(savedProject);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getProjects(Pageable pageable) {
        Pageable sanitizedPageable = com.issuemanagement.common.util.PageableUtils.sanitize(pageable);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Role currentUserRole = SecurityUtils.getCurrentUserRole();

        Page<Project> projectsPage;
        if (currentUserRole == Role.ADMIN) {
            projectsPage = projectRepository.findAll(sanitizedPageable);
        } else if (currentUserRole == Role.PROJECT_MANAGER) {
            projectsPage = projectRepository.findByManagerId(currentUserId, sanitizedPageable);
        } else {
            projectsPage = projectRepository.findProjectsForMember(currentUserId, sanitizedPageable);
        }

        return PageResponse.from(projectsPage.map(ProjectResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long projectId) {
        Project project = getProjectEntity(projectId);
        projectAuthService.checkProjectAccess(project);
        return ProjectResponse.fromEntity(project);
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, UpdateProjectRequest request) {
        Project project = getProjectEntity(projectId);

        if (project.isArchived()) {
            throw new BadRequestException("Archived projects cannot be modified");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Role currentUserRole = SecurityUtils.getCurrentUserRole();

        boolean isAdmin = currentUserRole == Role.ADMIN;
        boolean isAssignedPm = currentUserRole == Role.PROJECT_MANAGER
                && project.getManager() != null
                && project.getManager().getId().equals(currentUserId);

        if (!isAdmin && !isAssignedPm) {
            throw new ForbiddenException("You do not have permission to modify this project");
        }

        project.setName(request.getName().trim());
        project.setDescription(request.getDescription());

        Project updated = projectRepository.save(project);
        auditService.logCurrentActorEvent(
                "UPDATE_PROJECT",
                "Project",
                updated.getId().toString(),
                "Updated details for project " + updated.getProjectKey()
        );

        return ProjectResponse.fromEntity(updated);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse assignManager(Long projectId, Long managerId) {
        Project project = getProjectEntity(projectId);
        User newManager = userService.getEligibleProjectManagerEntity(managerId);

        String oldManagerEmail = project.getManager() != null ? project.getManager().getEmail() : "None";
        project.setManager(newManager);

        Project updated = projectRepository.save(project);
        auditService.logCurrentActorEvent(
                "REASSIGN_MANAGER",
                "Project",
                updated.getId().toString(),
                "Reassigned project " + updated.getProjectKey() + " manager from " + oldManagerEmail + " to " + newManager.getEmail()
        );

        return ProjectResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getProjectMembers(Long projectId) {
        Project project = getProjectEntity(projectId);
        projectAuthService.checkProjectAccess(project);

        List<ProjectMemberResponse> members = new ArrayList<>();
        if (project.getManager() != null) {
            User manager = project.getManager();
            members.add(ProjectMemberResponse.builder()
                    .id(-1L)
                    .projectId(project.getId())
                    .userId(manager.getId())
                    .userEmail(manager.getEmail())
                    .userName(manager.getFirstName() + " " + manager.getLastName())
                    .userRole(manager.getRole())
                    .joinedAt(project.getCreatedAt())
                    .build());
        }

        projectMemberRepository.findByProjectIdWithUser(projectId)
                .stream()
                .map(ProjectMemberResponse::fromEntity)
                .forEach(members::add);

        return members;
    }

    @Transactional
    public ProjectMemberResponse addProjectMember(Long projectId, Long userId) {
        Project project = getProjectEntity(projectId);

        if (project.isArchived()) {
            throw new BadRequestException("Cannot add members to an archived project");
        }

        projectAuthService.checkMembershipManagementPermission(project);

        if (project.getManager() != null && project.getManager().getId().equals(userId)) {
            throw new BadRequestException("The assigned Project Manager is already associated with the project and cannot be added as a regular member");
        }

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new ConflictException("User is already a member of this project");
        }

        User user = userService.getEligibleEmployeeEntity(userId);

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(user)
                .build();

        ProjectMember saved = projectMemberRepository.save(member);
        auditService.logCurrentActorEvent(
                "ADD_PROJECT_MEMBER",
                "ProjectMember",
                saved.getId().toString(),
                "Added employee " + user.getEmail() + " to project " + project.getProjectKey()
        );

        return ProjectMemberResponse.fromEntity(saved);
    }

    @Transactional
    public void removeProjectMember(Long projectId, Long userId) {
        Project project = getProjectEntity(projectId);

        if (project.isArchived()) {
            throw new BadRequestException("Cannot modify members in an archived project");
        }

        projectAuthService.checkMembershipManagementPermission(project);

        if (project.getManager() != null && project.getManager().getId().equals(userId)) {
            throw new BadRequestException("Cannot remove the assigned Project Manager via the employee membership endpoint");
        }

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + userId + " is not a member of this project"));

        // Unassign active issues (OPEN or IN_PROGRESS) in this project assigned to this member
        java.util.List<com.issuemanagement.issue.entity.Issue> assignedIssues =
                issueRepository.findByProjectIdAndAssigneeIdAndStatusIn(
                        projectId,
                        userId,
                        java.util.List.of(com.issuemanagement.issue.entity.IssueStatus.OPEN, com.issuemanagement.issue.entity.IssueStatus.IN_PROGRESS)
                );

        if (!assignedIssues.isEmpty()) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            User currentActor = userService.getUserEntity(currentUserId);
            String removedUserEmail = member.getUser().getEmail();

            for (com.issuemanagement.issue.entity.Issue issue : assignedIssues) {
                issue.setAssignee(null);
                issueRepository.save(issue);
                historyService.recordHistory(
                        issue,
                        currentActor,
                        com.issuemanagement.history.entity.ChangeType.ASSIGNEE_CHANGED,
                        "assignee",
                        removedUserEmail,
                        "Unassigned (Member Removed from Project)"
                );
            }
        }

        projectMemberRepository.delete(member);
        auditService.logCurrentActorEvent(
                "REMOVE_PROJECT_MEMBER",
                "ProjectMember",
                member.getId().toString(),
                "Removed employee ID " + userId + " (" + member.getUser().getEmail() + ") from project " + project.getProjectKey()
        );
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse archiveProject(Long projectId) {
        Project project = getProjectEntity(projectId);
        if (project.isArchived()) {
            throw new BadRequestException("Project is already archived");
        }

        project.setStatus(ProjectStatus.ARCHIVED);
        Project updated = projectRepository.save(project);

        auditService.logCurrentActorEvent(
                "ARCHIVE_PROJECT",
                "Project",
                updated.getId().toString(),
                "Archived project " + updated.getProjectKey()
        );

        return ProjectResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public Project getProjectEntity(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    @Transactional(readOnly = true)
    public Project getActiveProjectEntity(Long projectId) {
        Project project = getProjectEntity(projectId);
        if (project.isArchived()) {
            throw new BadRequestException("Project is archived: " + project.getProjectKey());
        }
        return project;
    }
}

package com.issuemanagement.issue.service;

import com.issuemanagement.audit.service.AuditService;
import com.issuemanagement.common.dto.PageResponse;
import com.issuemanagement.common.exception.BadRequestException;
import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.common.exception.ResourceNotFoundException;
import com.issuemanagement.history.entity.ChangeType;
import com.issuemanagement.history.service.IssueHistoryService;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.security.SecurityUtils;
import com.issuemanagement.identity.service.UserService;
import com.issuemanagement.issue.dto.CreateIssueRequest;
import com.issuemanagement.issue.dto.IssueResponse;
import com.issuemanagement.issue.dto.UpdateIssueRequest;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.issue.repository.IssueRepository;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.service.ProjectAuthorizationService;
import com.issuemanagement.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProjectService projectService;
    private final ProjectAuthorizationService projectAuthService;
    private final UserService userService;
    private final IssueHistoryService historyService;
    private final AuditService auditService;
    private final IssueVectorService issueVectorService;

    @Transactional
    public IssueResponse createIssue(Long projectId, CreateIssueRequest request) {
        Project project = projectService.getActiveProjectEntity(projectId);
        projectAuthService.checkProjectAccess(project);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        User reporter = userService.getActiveUserEntity(currentUserId);

        User assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = validateAndGetAssignee(project.getId(), request.getAssigneeId());
        }

        long issueSequence = issueRepository.countByProjectId(projectId) + 1;
        String issueKey = project.getProjectKey() + "-" + issueSequence;

        Issue issue = Issue.builder()
                .issueKey(issueKey)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .type(request.getType())
                .priority(request.getPriority())
                .status(IssueStatus.OPEN)
                .project(project)
                .reporter(reporter)
                .assignee(assignee)
                .archived(false)
                .build();

        Issue saved = issueRepository.save(issue);

        issueVectorService.indexIssue(issue);

        historyService.recordHistory(
                saved,
                reporter,
                ChangeType.CREATED,
                "status",
                null,
                IssueStatus.OPEN.name()
        );

        auditService.logCurrentActorEvent(
                "CREATE_ISSUE",
                "Issue",
                saved.getId().toString(),
                "Created issue " + saved.getIssueKey() + " in project " + project.getProjectKey()
        );

        return IssueResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<IssueResponse> getProjectIssues(
            Long projectId,
            IssueStatus status,
            IssuePriority priority,
            IssueType type,
            Long assigneeId,
            Pageable pageable) {
        Project project = projectService.getProjectEntity(projectId);
        projectAuthService.checkProjectAccess(project);

        Pageable sanitizedPageable = com.issuemanagement.common.util.PageableUtils.sanitize(pageable);
        Page<Issue> page = issueRepository.findProjectIssues(
                projectId, status, priority, type, assigneeId, sanitizedPageable);
        return PageResponse.from(page.map(IssueResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public IssueResponse getIssueById(Long issueId) {
        Issue issue = getIssueEntity(issueId);
        projectAuthService.checkProjectAccess(issue.getProject());
        return IssueResponse.fromEntity(issue);
    }

    @Transactional
    public IssueResponse updateIssue(Long issueId, UpdateIssueRequest request) {
        Issue issue = getIssueEntity(issueId);
        checkProjectActive(issue.getProject());
        projectAuthService.checkProjectAccess(issue.getProject());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Role currentUserRole = SecurityUtils.getCurrentUserRole();
        User currentActor = userService.getActiveUserEntity(currentUserId);

        if (currentUserRole == Role.EMPLOYEE) {
            boolean isReporter = issue.getReporter().getId().equals(currentUserId);
            boolean isAssignee = issue.getAssignee() != null && issue.getAssignee().getId().equals(currentUserId);
            if (!isReporter && !isAssignee) {
                throw new ForbiddenException("Employees can only update issues they reported or are assigned to");
            }
        }

        if (!Objects.equals(issue.getTitle(), request.getTitle())) {
            historyService.recordHistory(issue, currentActor, ChangeType.UPDATED, "title", issue.getTitle(), request.getTitle());
            issue.setTitle(request.getTitle().trim());
        }

        if (!Objects.equals(issue.getDescription(), request.getDescription())) {
            historyService.recordHistory(issue, currentActor, ChangeType.UPDATED, "description", issue.getDescription(), request.getDescription());
            issue.setDescription(request.getDescription());
        }

        if (issue.getType() != request.getType()) {
            historyService.recordHistory(issue, currentActor, ChangeType.UPDATED, "type", issue.getType().name(), request.getType().name());
            issue.setType(request.getType());
        }

        if (issue.getPriority() != request.getPriority()) {
            historyService.recordHistory(issue, currentActor, ChangeType.PRIORITY_CHANGED, "priority", issue.getPriority().name(), request.getPriority().name());
            issue.setPriority(request.getPriority());
        }

        Issue updated = issueRepository.save(issue);
        issueVectorService.indexIssue(updated);
        return IssueResponse.fromEntity(updated);
    }

    @Transactional
    public IssueResponse assignIssue(Long issueId, Long assigneeId) {
        Issue issue = getIssueEntity(issueId);
        checkProjectActive(issue.getProject());
        projectAuthService.checkProjectAccess(issue.getProject());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Role currentUserRole = SecurityUtils.getCurrentUserRole();
        User currentActor = userService.getActiveUserEntity(currentUserId);

        boolean isAdmin = currentUserRole == Role.ADMIN;
        boolean isPm = currentUserRole == Role.PROJECT_MANAGER
                && issue.getProject().getManager() != null
                && issue.getProject().getManager().getId().equals(currentUserId);

        if (!isAdmin && !isPm) {
            throw new ForbiddenException("Only an Admin or the assigned Project Manager can assign issues");
        }

        String oldAssigneeName = issue.getAssignee() != null ? issue.getAssignee().getEmail() : "Unassigned";
        User newAssignee = null;
        if (assigneeId != null) {
            newAssignee = validateAndGetAssignee(issue.getProject().getId(), assigneeId);
        }

        issue.setAssignee(newAssignee);
        Issue updated = issueRepository.save(issue);

        String newAssigneeName = newAssignee != null ? newAssignee.getEmail() : "Unassigned";
        historyService.recordHistory(
                updated,
                currentActor,
                ChangeType.ASSIGNEE_CHANGED,
                "assignee",
                oldAssigneeName,
                newAssigneeName
        );

        return IssueResponse.fromEntity(updated);
    }

    @Transactional
    public IssueResponse transitionStatus(Long issueId, IssueStatus targetStatus) {
        Issue issue = getIssueEntity(issueId);
        checkProjectActive(issue.getProject());
        projectAuthService.checkProjectAccess(issue.getProject());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Role currentUserRole = SecurityUtils.getCurrentUserRole();
        User currentActor = userService.getActiveUserEntity(currentUserId);

        if (issue.getStatus() == IssueStatus.CLOSED && targetStatus == IssueStatus.OPEN) {
            boolean isAdmin = currentUserRole == Role.ADMIN;
            boolean isPm = currentUserRole == Role.PROJECT_MANAGER
                    && issue.getProject().getManager() != null
                    && issue.getProject().getManager().getId().equals(currentUserId);
            if (!isAdmin && !isPm) {
                throw new ForbiddenException("Only an Admin or Project Manager can reopen closed issues");
            }
        }

        if (currentUserRole == Role.EMPLOYEE) {
            boolean isAssignee = issue.getAssignee() != null && issue.getAssignee().getId().equals(currentUserId);
            boolean isReporter = issue.getReporter().getId().equals(currentUserId);
            if (!isAssignee && !isReporter) {
                throw new ForbiddenException("Employees can only transition the status of issues they are assigned to or reported");
            }
        }

        if (!issue.getStatus().canTransitionTo(targetStatus)) {
            throw new BadRequestException(String.format(
                    "Invalid status transition from '%s' to '%s'",
                    issue.getStatus(), targetStatus));
        }

        IssueStatus oldStatus = issue.getStatus();
        issue.setStatus(targetStatus);
        Issue updated = issueRepository.save(issue);

        historyService.recordHistory(
                updated,
                currentActor,
                ChangeType.STATUS_CHANGED,
                "status",
                oldStatus.name(),
                targetStatus.name()
        );

        return IssueResponse.fromEntity(updated);
    }

    @Transactional
    public IssueResponse reopenIssue(Long issueId, String reason) {
        Issue issue = getIssueEntity(issueId);
        checkProjectActive(issue.getProject());
        projectAuthService.checkProjectAccess(issue.getProject());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        User currentActor = userService.getActiveUserEntity(currentUserId);

        if (issue.getStatus() == IssueStatus.OPEN) {
            return IssueResponse.fromEntity(issue);
        }

        IssueStatus oldStatus = issue.getStatus();
        issue.setStatus(IssueStatus.OPEN);
        Issue updated = issueRepository.save(issue);

        String detail = (reason != null && !reason.isBlank())
                ? "Reopened by project member: " + reason.trim()
                : "Reopened by project member";

        historyService.recordHistory(
                updated,
                currentActor,
                ChangeType.STATUS_CHANGED,
                "status",
                oldStatus.name(),
                IssueStatus.OPEN.name()
        );

        auditService.logCurrentActorEvent(
                "REOPEN_ISSUE",
                "Issue",
                issue.getId().toString(),
                detail
        );

        return IssueResponse.fromEntity(updated);
    }

    @Transactional
    public void archiveIssue(Long issueId) {
        Issue issue = getIssueEntity(issueId);
        checkProjectActive(issue.getProject());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Role currentUserRole = SecurityUtils.getCurrentUserRole();
        User currentActor = userService.getActiveUserEntity(currentUserId);

        boolean isAdmin = currentUserRole == Role.ADMIN;
        boolean isPm = currentUserRole == Role.PROJECT_MANAGER
                && issue.getProject().getManager() != null
                && issue.getProject().getManager().getId().equals(currentUserId);

        if (!isAdmin && !isPm) {
            throw new ForbiddenException("Only an Admin or assigned Project Manager can archive issues");
        }

        issue.setArchived(true);
        issueRepository.save(issue);
        issueVectorService.deleteIndex(issueId);

        historyService.recordHistory(
                issue,
                currentActor,
                ChangeType.ARCHIVED,
                "archived",
                "false",
                "true"
        );
    }

    @Transactional(readOnly = true)
    public Issue getIssueEntity(Long issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", "id", issueId));
    }

    private User validateAndGetAssignee(Long projectId, Long assigneeId) {
        User assignee = userService.getEligibleAssigneeEntity(assigneeId);
        Project project = projectService.getProjectEntity(projectId);

        boolean isManager = project.getManager() != null && project.getManager().getId().equals(assigneeId);
        boolean isMember = projectAuthService.isProjectMember(projectId, assigneeId);

        if (!isManager && !isMember) {
            throw new BadRequestException("User " + assignee.getEmail() + " is neither the assigned project manager nor a member of project ID " + projectId);
        }

        return assignee;
    }

    private void checkProjectActive(Project project) {
        if (project.isArchived()) {
            throw new BadRequestException("Project is archived: " + project.getProjectKey());
        }
    }
}

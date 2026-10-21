package com.issuemanagement.comment.service;

import com.issuemanagement.comment.dto.CommentResponse;
import com.issuemanagement.comment.dto.CreateCommentRequest;
import com.issuemanagement.comment.dto.UpdateCommentRequest;
import com.issuemanagement.comment.entity.IssueComment;
import com.issuemanagement.comment.repository.IssueCommentRepository;
import com.issuemanagement.common.exception.BadRequestException;
import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.common.exception.ResourceNotFoundException;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.security.SecurityUtils;
import com.issuemanagement.identity.service.UserService;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.service.IssueService;
import com.issuemanagement.project.service.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final IssueCommentRepository commentRepository;
    private final IssueService issueService;
    private final UserService userService;
    private final ProjectAuthorizationService projectAuthService;

    @Transactional
    public CommentResponse addComment(Long issueId, CreateCommentRequest request) {
        Issue issue = issueService.getIssueEntity(issueId);

        if (issue.getProject().isArchived()) {
            throw new BadRequestException("Cannot comment on issues in an archived project");
        }
        if (issue.isArchived()) {
            throw new BadRequestException("Cannot comment on an archived issue");
        }

        projectAuthService.checkProjectAccess(issue.getProject());

        Long currentUserId = SecurityUtils.getCurrentUserId();
        User author = userService.getActiveUserEntity(currentUserId);

        IssueComment comment = IssueComment.builder()
                .issue(issue)
                .author(author)
                .content(request.getContent().trim())
                .build();

        IssueComment saved = commentRepository.save(comment);
        return CommentResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsForIssue(Long issueId) {
        Issue issue = issueService.getIssueEntity(issueId);
        projectAuthService.checkProjectAccess(issue.getProject());

        return commentRepository.findByIssueIdWithAuthor(issueId)
                .stream()
                .map(CommentResponse::fromEntity)
                .toList();
    }

    @Transactional
    public CommentResponse updateComment(Long commentId, UpdateCommentRequest request) {
        IssueComment comment = getCommentEntity(commentId);
        com.issuemanagement.project.entity.Project project = comment.getIssue().getProject();

        if (project.isArchived()) {
            throw new BadRequestException("Cannot edit comments in an archived project");
        }

        // Enforce project access first
        projectAuthService.checkProjectAccess(project);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isCurrentUserAdmin();

        if (!isAdmin && !comment.getAuthor().getId().equals(currentUserId)) {
            throw new ForbiddenException("You can only edit your own comments");
        }

        comment.setContent(request.getContent().trim());
        IssueComment updated = commentRepository.save(comment);
        return CommentResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        IssueComment comment = getCommentEntity(commentId);
        com.issuemanagement.project.entity.Project project = comment.getIssue().getProject();

        if (project.isArchived()) {
            throw new BadRequestException("Cannot delete comments in an archived project");
        }

        // Enforce project access first
        projectAuthService.checkProjectAccess(project);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Role currentUserRole = SecurityUtils.getCurrentUserRole();

        boolean isAdmin = currentUserRole == Role.ADMIN;
        boolean isPm = currentUserRole == Role.PROJECT_MANAGER
                && project.getManager() != null
                && project.getManager().getId().equals(currentUserId);
        boolean isAuthor = comment.getAuthor().getId().equals(currentUserId);

        if (!isAdmin && !isPm && !isAuthor) {
            throw new ForbiddenException("You do not have permission to delete this comment");
        }

        commentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public IssueComment getCommentEntity(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));
    }
}

package com.issuemanagement.comment.service;

import com.issuemanagement.comment.dto.CommentResponse;
import com.issuemanagement.comment.dto.UpdateCommentRequest;
import com.issuemanagement.comment.entity.IssueComment;
import com.issuemanagement.comment.repository.IssueCommentRepository;
import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.security.UserPrincipal;
import com.issuemanagement.identity.service.UserService;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.service.IssueService;
import com.issuemanagement.project.entity.Project;
import com.issuemanagement.project.entity.ProjectStatus;
import com.issuemanagement.project.service.ProjectAuthorizationService;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private IssueCommentRepository commentRepository;

    @Mock
    private IssueService issueService;

    @Mock
    private UserService userService;

    @Mock
    private ProjectAuthorizationService projectAuthService;

    @InjectMocks
    private CommentService commentService;

    private User adminUser;
    private User pmUser;
    private User employeeUser;
    private User otherPmUser;
    private Project testProject;
    private Issue testIssue;
    private IssueComment testComment;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(1L).email("admin@test.com").role(Role.ADMIN).active(true).build();
        pmUser = User.builder().id(2L).email("pm@test.com").role(Role.PROJECT_MANAGER).active(true).build();
        otherPmUser = User.builder().id(3L).email("otherpm@test.com").role(Role.PROJECT_MANAGER).active(true).build();
        employeeUser = User.builder().id(4L).email("emp@test.com").role(Role.EMPLOYEE).active(true).build();

        testProject = Project.builder()
                .id(10L)
                .name("Project Alpha")
                .projectKey("PA")
                .status(ProjectStatus.ACTIVE)
                .manager(pmUser)
                .build();

        testIssue = Issue.builder()
                .id(100L)
                .project(testProject)
                .title("Issue 1")
                .build();

        testComment = IssueComment.builder()
                .id(1000L)
                .issue(testIssue)
                .author(employeeUser)
                .content("Original content")
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
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
    @DisplayName("updateComment: succeeds when author has project access")
    void updateComment_Success_WhenAuthorHasProjectAccess() {
        authenticate(employeeUser);
        when(commentRepository.findById(1000L)).thenReturn(Optional.of(testComment));
        when(commentRepository.save(any(IssueComment.class))).thenAnswer(i -> i.getArgument(0));

        UpdateCommentRequest request = new UpdateCommentRequest("Updated content");
        CommentResponse response = commentService.updateComment(1000L, request);

        assertThat(response.getContent()).isEqualTo("Updated content");
        verify(projectAuthService).checkProjectAccess(testProject);
        verify(commentRepository).save(testComment);
    }

    @Test
    @DisplayName("updateComment: throws ForbiddenException when removed member loses project access")
    void updateComment_ThrowsForbidden_WhenRemovedMemberLosesProjectAccess() {
        authenticate(employeeUser);
        when(commentRepository.findById(1000L)).thenReturn(Optional.of(testComment));
        doThrow(new ForbiddenException("You do not have access to this project"))
                .when(projectAuthService).checkProjectAccess(testProject);

        UpdateCommentRequest request = new UpdateCommentRequest("Updated content");

        assertThatThrownBy(() -> commentService.updateComment(1000L, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You do not have access to this project");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateComment: throws ForbiddenException when non-author attempts to edit comment")
    void updateComment_ThrowsForbidden_WhenNotAuthorAndNotAdmin() {
        authenticate(pmUser);
        when(commentRepository.findById(1000L)).thenReturn(Optional.of(testComment));

        UpdateCommentRequest request = new UpdateCommentRequest("Updated content");

        assertThatThrownBy(() -> commentService.updateComment(1000L, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You can only edit your own comments");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteComment: succeeds when user is assigned project manager")
    void deleteComment_Success_WhenAssignedPm() {
        authenticate(pmUser);
        when(commentRepository.findById(1000L)).thenReturn(Optional.of(testComment));

        commentService.deleteComment(1000L);

        verify(projectAuthService).checkProjectAccess(testProject);
        verify(commentRepository).delete(testComment);
    }

    @Test
    @DisplayName("deleteComment: throws ForbiddenException when reassigned PM is no longer manager and not author")
    void deleteComment_ThrowsForbidden_WhenReassignedPmNoLongerManager() {
        authenticate(otherPmUser);
        when(commentRepository.findById(1000L)).thenReturn(Optional.of(testComment));

        assertThatThrownBy(() -> commentService.deleteComment(1000L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You do not have permission to delete this comment");

        verify(projectAuthService).checkProjectAccess(testProject);
        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteComment: throws ForbiddenException when user lacks project access")
    void deleteComment_ThrowsForbidden_WhenUserLacksProjectAccess() {
        authenticate(employeeUser);
        when(commentRepository.findById(1000L)).thenReturn(Optional.of(testComment));
        doThrow(new ForbiddenException("You do not have access to this project"))
                .when(projectAuthService).checkProjectAccess(testProject);

        assertThatThrownBy(() -> commentService.deleteComment(1000L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You do not have access to this project");

        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteComment: succeeds when user is admin")
    void deleteComment_Success_WhenAdmin() {
        authenticate(adminUser);
        when(commentRepository.findById(1000L)).thenReturn(Optional.of(testComment));

        commentService.deleteComment(1000L);

        verify(projectAuthService).checkProjectAccess(testProject);
        verify(commentRepository).delete(testComment);
    }
}

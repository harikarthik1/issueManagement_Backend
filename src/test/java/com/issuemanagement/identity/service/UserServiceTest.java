package com.issuemanagement.identity.service;

import com.issuemanagement.audit.service.AuditService;
import com.issuemanagement.common.exception.BadRequestException;
import com.issuemanagement.history.entity.ChangeType;
import com.issuemanagement.history.service.IssueHistoryService;
import com.issuemanagement.identity.dto.UserResponse;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.repository.UserRepository;
import com.issuemanagement.identity.security.UserPrincipal;
import com.issuemanagement.issue.entity.Issue;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.repository.IssueRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private IssueHistoryService historyService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    private User adminUser;
    private User employeeUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .email("admin@test.com")
                .role(Role.ADMIN)
                .active(true)
                .build();

        employeeUser = User.builder()
                .id(2L)
                .email("emp@test.com")
                .role(Role.EMPLOYEE)
                .active(true)
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
    @DisplayName("updateUserStatus: deactivation unassigns active (OPEN and IN_PROGRESS) issues with history")
    void updateUserStatus_Deactivation_UnassignsActiveIssues() {
        authenticate(adminUser);
        when(userRepository.findById(2L)).thenReturn(Optional.of(employeeUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Issue openIssue = Issue.builder()
                .id(101L)
                .title("Open Issue")
                .status(IssueStatus.OPEN)
                .assignee(employeeUser)
                .build();

        Issue inProgressIssue = Issue.builder()
                .id(102L)
                .title("In Progress Issue")
                .status(IssueStatus.IN_PROGRESS)
                .assignee(employeeUser)
                .build();

        when(issueRepository.findByAssigneeIdAndStatusIn(eq(2L), any()))
                .thenReturn(List.of(openIssue, inProgressIssue));

        UserResponse response = userService.updateUserStatus(2L, false);

        assertThat(response.isActive()).isFalse();
        assertThat(openIssue.getAssignee()).isNull();
        assertThat(inProgressIssue.getAssignee()).isNull();

        verify(issueRepository).save(openIssue);
        verify(issueRepository).save(inProgressIssue);

        verify(historyService).recordHistory(
                eq(openIssue),
                eq(adminUser),
                eq(ChangeType.ASSIGNEE_CHANGED),
                eq("assignee"),
                eq("emp@test.com"),
                eq("Unassigned (Account Deactivated)")
        );

        verify(historyService).recordHistory(
                eq(inProgressIssue),
                eq(adminUser),
                eq(ChangeType.ASSIGNEE_CHANGED),
                eq("assignee"),
                eq("emp@test.com"),
                eq("Unassigned (Account Deactivated)")
        );

        verify(auditService).logCurrentActorEvent(
                eq("DEACTIVATE_USER"),
                eq("User"),
                eq("2"),
                contains("emp@test.com")
        );
    }

    @Test
    @DisplayName("updateUserStatus: throws BadRequestException when admin attempts self-deactivation")
    void updateUserStatus_SelfDeactivation_ThrowsBadRequest() {
        authenticate(adminUser);

        assertThatThrownBy(() -> userService.updateUserStatus(1L, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Administrators cannot deactivate their own account");

        verify(userRepository, never()).save(any());
        verify(issueRepository, never()).findByAssigneeIdAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("updateUserStatus: activation sets active true and logs event")
    void updateUserStatus_Activation_Succeeds() {
        authenticate(adminUser);
        employeeUser.setActive(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(employeeUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserStatus(2L, true);

        assertThat(response.isActive()).isTrue();
        verify(issueRepository, never()).findByAssigneeIdAndStatusIn(any(), any());
        verify(auditService).logCurrentActorEvent(
                eq("ACTIVATE_USER"),
                eq("User"),
                eq("2"),
                contains("emp@test.com")
        );
    }

    @Test
    @DisplayName("getEligibleEmployees: returns only active employees")
    void getEligibleEmployees_ReturnsOnlyActive() {
        when(userRepository.findByRoleAndActiveTrue(Role.EMPLOYEE)).thenReturn(List.of(employeeUser));

        List<UserResponse> result = userService.getEligibleEmployees();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("emp@test.com");
    }

    @Test
    @DisplayName("getEligibleEmployeeEntity: throws BadRequestException when user is inactive")
    void getEligibleEmployeeEntity_ThrowsBadRequestWhenInactive() {
        employeeUser.setActive(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(employeeUser));

        assertThatThrownBy(() -> userService.getEligibleEmployeeEntity(2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("User account is deactivated");
    }
}

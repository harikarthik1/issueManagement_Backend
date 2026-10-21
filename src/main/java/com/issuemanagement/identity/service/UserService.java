package com.issuemanagement.identity.service;

import com.issuemanagement.common.dto.PageResponse;
import com.issuemanagement.common.exception.BadRequestException;
import com.issuemanagement.common.exception.ConflictException;
import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.common.exception.ResourceNotFoundException;
import com.issuemanagement.identity.dto.CreateUserRequest;
import com.issuemanagement.identity.dto.UpdateUserRequest;
import com.issuemanagement.identity.dto.UserResponse;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.repository.UserRepository;
import com.issuemanagement.identity.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.issuemanagement.issue.repository.IssueRepository issueRepository;
    private final com.issuemanagement.history.service.IssueHistoryService historyService;
    private final com.issuemanagement.audit.service.AuditService auditService;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating user with email: {} and role: {}", request.getEmail(), request.getRole());
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new ConflictException("Email already in use: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .role(request.getRole())
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isCurrentUserAdmin();

        if (!isAdmin && !currentUserId.equals(userId)) {
            throw new ForbiddenException("You are not authorized to view this user's profile");
        }

        User user = getUserEntity(userId);
        return UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> getAllUsers(Role role, Pageable pageable) {
        Pageable sanitizedPageable = com.issuemanagement.common.util.PageableUtils.sanitize(pageable);
        Page<User> page = (role != null)
                ? userRepository.findByRole(role, sanitizedPageable)
                : userRepository.findAll(sanitizedPageable);
        return PageResponse.from(page.map(UserResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public List<UserResponse> getEligibleEmployees() {
        return userRepository.findByRoleAndActiveTrue(Role.EMPLOYEE)
                .stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getEligibleProjectManagers() {
        return userRepository.findByRoleAndActiveTrue(Role.PROJECT_MANAGER)
                .stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isCurrentUserAdmin();

        if (!isAdmin && !currentUserId.equals(userId)) {
            throw new ForbiddenException("You are not authorized to update this user's profile");
        }

        User user = getUserEntity(userId);
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());

        User updatedUser = userRepository.save(user);
        return UserResponse.fromEntity(updatedUser);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUserStatus(Long userId, boolean active) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId.equals(userId) && !active) {
            throw new BadRequestException("Administrators cannot deactivate their own account");
        }

        User user = getUserEntity(userId);
        user.setActive(active);
        User updated = userRepository.save(user);

        if (!active) {
            // Find all active assigned issues across every project with status OPEN or IN_PROGRESS
            java.util.List<com.issuemanagement.issue.entity.Issue> activeAssignedIssues =
                    issueRepository.findByAssigneeIdAndStatusIn(
                            userId,
                            java.util.List.of(
                                    com.issuemanagement.issue.entity.IssueStatus.OPEN,
                                    com.issuemanagement.issue.entity.IssueStatus.IN_PROGRESS
                            )
                    );

            if (!activeAssignedIssues.isEmpty()) {
                User currentActor = getUserEntity(currentUserId);
                String userEmail = user.getEmail();

                for (com.issuemanagement.issue.entity.Issue issue : activeAssignedIssues) {
                    issue.setAssignee(null);
                    issueRepository.save(issue);
                    historyService.recordHistory(
                            issue,
                            currentActor,
                            com.issuemanagement.history.entity.ChangeType.ASSIGNEE_CHANGED,
                            "assignee",
                            userEmail,
                            "Unassigned (Account Deactivated)"
                    );
                }
            }

            auditService.logCurrentActorEvent(
                    "DEACTIVATE_USER",
                    "User",
                    userId.toString(),
                    "Deactivated user account: " + user.getEmail()
            );
        } else {
            auditService.logCurrentActorEvent(
                    "ACTIVATE_USER",
                    "User",
                    userId.toString(),
                    "Activated user account: " + user.getEmail()
            );
        }

        log.info("Updated active status for user ID {} to {}", userId, active);
        return UserResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public User getUserEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Transactional(readOnly = true)
    public User getActiveUserEntity(Long userId) {
        User user = getUserEntity(userId);
        if (!user.isActive()) {
            throw new BadRequestException("User account is deactivated: " + user.getEmail());
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getEligibleEmployeeEntity(Long userId) {
        User user = getActiveUserEntity(userId);
        if (user.getRole() != Role.EMPLOYEE) {
            throw new BadRequestException("User with ID " + userId + " does not have the EMPLOYEE role");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getEligibleProjectManagerEntity(Long userId) {
        User user = getActiveUserEntity(userId);
        if (user.getRole() != Role.PROJECT_MANAGER) {
            throw new BadRequestException("User with ID " + userId + " does not have the PROJECT_MANAGER role");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getEligibleAssigneeEntity(Long userId) {
        User user = getActiveUserEntity(userId);
        if (user.getRole() != Role.EMPLOYEE && user.getRole() != Role.PROJECT_MANAGER) {
            throw new BadRequestException("User with ID " + userId + " must have either EMPLOYEE or PROJECT_MANAGER role to be assigned issues");
        }
        return user;
    }
}

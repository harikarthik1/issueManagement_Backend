package com.issuemanagement.identity.service;

import com.issuemanagement.common.exception.ConflictException;
import com.issuemanagement.identity.dto.AuthResponse;
import com.issuemanagement.identity.dto.LoginRequest;
import com.issuemanagement.identity.dto.RegisterAdminRequest;
import com.issuemanagement.identity.dto.UserResponse;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.security.JwtTokenProvider;
import com.issuemanagement.identity.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final com.issuemanagement.identity.repository.UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.issuemanagement.audit.service.AuditService auditService;
    private final com.issuemanagement.assistant.service.AssistantConversationService assistantConversationService;

    @Transactional
    public UserResponse registerAdmin(RegisterAdminRequest request) {
        log.info("Attempting admin registration for email: {}", request.getEmail());

        if (userRepository.existsByRole(Role.ADMIN)) {
            throw new ConflictException("An administrator account already exists. Only one admin registration is allowed.");
        }

        String normalizedEmail = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email already in use: " + request.getEmail());
        }

        User admin = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .role(Role.ADMIN)
                .active(true)
                .passwordChangeRequired(false)
                .build();

        User savedAdmin = userRepository.save(admin);

        auditService.logEvent(
                savedAdmin,
                "REGISTER_ADMIN",
                "User",
                savedAdmin.getId().toString(),
                "Administrator account registered: " + savedAdmin.getEmail()
        );

        log.info("Successfully registered administrator with ID: {} and email: {}", savedAdmin.getId(), savedAdmin.getEmail());
        return UserResponse.fromEntity(savedAdmin);
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Attempting login for user: {}", request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = tokenProvider.generateToken(principal);

        UserResponse userResponse = UserResponse.builder()
                .id(principal.getId())
                .email(principal.getEmail())
                .firstName(principal.getFirstName())
                .lastName(principal.getLastName())
                .role(principal.getRole())
                .active(principal.isActive())
                .passwordChangeRequired(principal.isPasswordChangeRequired())
                .build();

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getExpirationMs())
                .user(userResponse)
                .build();
    }

    @org.springframework.transaction.annotation.Transactional
    public AuthResponse changePassword(com.issuemanagement.identity.dto.ChangePasswordRequest request) {
        Long currentUserId = com.issuemanagement.identity.security.SecurityUtils.getCurrentUserId();
        com.issuemanagement.identity.entity.User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new com.issuemanagement.common.exception.ResourceNotFoundException("User", "id", currentUserId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new com.issuemanagement.common.exception.BadRequestException("Current password does not match");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new com.issuemanagement.common.exception.BadRequestException("New password cannot be the same as the existing password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangeRequired(false);
        com.issuemanagement.identity.entity.User updatedUser = userRepository.save(user);

        auditService.logCurrentActorEvent(
                "CHANGE_PASSWORD",
                "User",
                updatedUser.getId().toString(),
                "User successfully updated their password"
        );

        UserPrincipal newPrincipal = UserPrincipal.create(updatedUser);
        String newToken = tokenProvider.generateToken(newPrincipal);

        return AuthResponse.builder()
                .token(newToken)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getExpirationMs())
                .user(UserResponse.fromEntity(updatedUser))
                .build();
    }

    public void logout() {
        try {
            String sessionId = com.issuemanagement.identity.security.SecurityUtils.getCurrentSessionId();
            Long userId = com.issuemanagement.identity.security.SecurityUtils.getCurrentUserId();
            log.info("Processing logout for user {} with session {}", userId, sessionId);
            if (sessionId != null) {
                assistantConversationService.deleteAllConversationsForSession(sessionId);
            }
        } catch (Exception ex) {
            log.warn("Error during session assistant conversations cleanup on logout: {}", ex.getMessage());
        }
    }
}

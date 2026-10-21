package com.issuemanagement.identity.service;

import com.issuemanagement.audit.service.AuditService;
import com.issuemanagement.common.exception.ConflictException;
import com.issuemanagement.identity.dto.RegisterAdminRequest;
import com.issuemanagement.identity.dto.UserResponse;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.entity.User;
import com.issuemanagement.identity.repository.UserRepository;
import com.issuemanagement.identity.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @Mock
    private com.issuemanagement.assistant.service.AssistantConversationService assistantConversationService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("registerAdmin: successfully registers admin when no admin exists")
    void registerAdmin_SuccessWhenNoAdminExists() {
        RegisterAdminRequest request = RegisterAdminRequest.builder()
                .email("admin@company.com")
                .password("Password123!")
                .firstName("John")
                .lastName("Doe")
                .build();

        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@company.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded_pass");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        UserResponse response = authService.registerAdmin(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getEmail()).isEqualTo("admin@company.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.isActive()).isTrue();
        assertThat(response.isPasswordChangeRequired()).isFalse();

        verify(auditService).logEvent(
                any(User.class),
                eq("REGISTER_ADMIN"),
                eq("User"),
                eq("10"),
                contains("admin@company.com")
        );
    }

    @Test
    @DisplayName("registerAdmin: throws ConflictException when admin already exists")
    void registerAdmin_ThrowsConflictWhenAdminAlreadyExists() {
        RegisterAdminRequest request = RegisterAdminRequest.builder()
                .email("second_admin@company.com")
                .password("Password123!")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> authService.registerAdmin(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An administrator account already exists. Only one admin registration is allowed.");

        verify(userRepository, never()).save(any());
        verify(auditService, never()).logEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("registerAdmin: throws ConflictException when email is already in use")
    void registerAdmin_ThrowsConflictWhenEmailAlreadyInUse() {
        RegisterAdminRequest request = RegisterAdminRequest.builder()
                .email("existing@company.com")
                .password("Password123!")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("existing@company.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerAdmin(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already in use: existing@company.com");

        verify(userRepository, never()).save(any());
        verify(auditService, never()).logEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("logout: deletes assistant conversations for current session")
    void logout_PurgesSessionConversations() {
        com.issuemanagement.identity.security.UserPrincipal principal =
                com.issuemanagement.identity.security.UserPrincipal.builder()
                        .id(5L)
                        .email("logout.user@test.com")
                        .role(Role.EMPLOYEE)
                        .sessionId("session-logout-xyz")
                        .authorities(java.util.List.of())
                        .build();

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        try {
            authService.logout();
            verify(assistantConversationService).deleteAllConversationsForSession("session-logout-xyz");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}

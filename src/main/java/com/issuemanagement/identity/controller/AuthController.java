package com.issuemanagement.identity.controller;

import com.issuemanagement.common.dto.ApiResponse;
import com.issuemanagement.identity.dto.AuthResponse;
import com.issuemanagement.identity.dto.LoginRequest;
import com.issuemanagement.identity.dto.RegisterAdminRequest;
import com.issuemanagement.identity.dto.UserResponse;
import com.issuemanagement.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication and token issuance")
public class AuthController {

    private final AuthService authService;

    @PostMapping({"/register-admin", "/register"})
    @Operation(summary = "Register the initial administrator account (allowed for one admin only without secret key)")
    public ResponseEntity<ApiResponse<UserResponse>> registerAdmin(@Valid @RequestBody RegisterAdminRequest request) {
        UserResponse userResponse = authService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Administrator registered successfully", userResponse));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", authResponse));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password (required on initial admin login or for password update)")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(@Valid @RequestBody com.issuemanagement.identity.dto.ChangePasswordRequest request) {
        AuthResponse authResponse = authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", authResponse));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user and immediately purge temporary assistant conversations for this session")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }
}

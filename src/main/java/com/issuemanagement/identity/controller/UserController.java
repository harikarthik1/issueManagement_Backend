package com.issuemanagement.identity.controller;

import com.issuemanagement.common.dto.ApiResponse;
import com.issuemanagement.common.dto.PageResponse;
import com.issuemanagement.identity.dto.CreateUserRequest;
import com.issuemanagement.identity.dto.UpdateUserRequest;
import com.issuemanagement.identity.dto.UserResponse;
import com.issuemanagement.identity.dto.UserStatusUpdateRequest;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Endpoints for user management, role assignments, and account activation")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create a new user (Admin only)")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("User created successfully", response));
    }

    @GetMapping
    @Operation(summary = "List users with optional role filtering and pagination (Admin only)")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(required = false) Role role,
            @org.springdoc.core.annotations.ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<UserResponse> response = userService.getAllUsers(role, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user details by ID (Admin or Self)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long userId) {
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Update user details (Admin or Self)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", response));
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "Activate or deactivate a user account (Admin only)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        UserResponse response = userService.updateUserStatus(userId, request.getActive());
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", response));
    }

    @GetMapping("/eligible/employees")
    @Operation(summary = "List eligible active employees for project assignment")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getEligibleEmployees() {
        List<UserResponse> response = userService.getEligibleEmployees();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/eligible/managers")
    @Operation(summary = "List eligible active project managers (Admin only)")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getEligibleProjectManagers() {
        List<UserResponse> response = userService.getEligibleProjectManagers();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

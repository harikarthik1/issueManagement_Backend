package com.issuemanagement.project.controller;

import com.issuemanagement.common.dto.ApiResponse;
import com.issuemanagement.common.dto.PageResponse;
import com.issuemanagement.project.dto.*;
import com.issuemanagement.project.service.ProjectService;
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
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Endpoints for project management, lifecycle, and project team membership")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a new project with assigned manager (Admin only)")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Project created successfully", response));
    }

    @GetMapping
    @Operation(summary = "List projects scoped to user permissions (Admin: all, PM: managed, Employee: member)")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> getProjects(
            @org.springdoc.core.annotations.ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ProjectResponse> response = projectService.getProjects(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project details by ID (Admin, assigned PM, or project member)")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectById(@PathVariable Long projectId) {
        ProjectResponse response = projectService.getProjectById(projectId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Update permitted project details (Admin or assigned PM)")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        ProjectResponse response = projectService.updateProject(projectId, request);
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully", response));
    }

    @PutMapping("/{projectId}/manager")
    @Operation(summary = "Assign or reassign the project manager (Admin only)")
    public ResponseEntity<ApiResponse<ProjectResponse>> assignManager(
            @PathVariable Long projectId,
            @Valid @RequestBody AssignManagerRequest request) {
        ProjectResponse response = projectService.assignManager(projectId, request.getManagerId());
        return ResponseEntity.ok(ApiResponse.success("Project manager reassigned successfully", response));
    }

    @GetMapping("/{projectId}/members")
    @Operation(summary = "List project members (Admin, assigned PM, or project member)")
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> getProjectMembers(@PathVariable Long projectId) {
        List<ProjectMemberResponse> members = projectService.getProjectMembers(projectId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PostMapping("/{projectId}/members")
    @Operation(summary = "Add an active employee to the project (Admin or assigned PM)")
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> addProjectMember(
            @PathVariable Long projectId,
            @Valid @RequestBody AddMemberRequest request) {
        ProjectMemberResponse response = projectService.addProjectMember(projectId, request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Project member added successfully", response));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @Operation(summary = "Remove an employee from the project (Admin or assigned PM)")
    public ResponseEntity<Void> removeProjectMember(
            @PathVariable Long projectId,
            @PathVariable Long userId) {
        projectService.removeProjectMember(projectId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/archive")
    @Operation(summary = "Archive an active project (Admin only)")
    public ResponseEntity<ApiResponse<ProjectResponse>> archiveProject(@PathVariable Long projectId) {
        ProjectResponse response = projectService.archiveProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("Project archived successfully", response));
    }
}

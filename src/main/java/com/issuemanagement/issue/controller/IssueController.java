package com.issuemanagement.issue.controller;

import com.issuemanagement.common.dto.ApiResponse;
import com.issuemanagement.common.dto.PageResponse;
import com.issuemanagement.history.dto.IssueHistoryResponse;
import com.issuemanagement.history.service.IssueHistoryService;
import com.issuemanagement.issue.dto.*;
import com.issuemanagement.issue.entity.IssuePriority;
import com.issuemanagement.issue.entity.IssueStatus;
import com.issuemanagement.issue.entity.IssueType;
import com.issuemanagement.issue.service.IssueService;
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
@RequiredArgsConstructor
@Tag(name = "Issues", description = "Endpoints for issue management, assignment, workflows, and history")
public class IssueController {

    private final IssueService issueService;
    private final IssueHistoryService historyService;

    @PostMapping("/api/v1/projects/{projectId}/issues")
    @Operation(summary = "Create an issue in a project")
    public ResponseEntity<ApiResponse<IssueResponse>> createIssue(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateIssueRequest request) {
        IssueResponse response = issueService.createIssue(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Issue created successfully", response));
    }

    @GetMapping("/api/v1/projects/{projectId}/issues")
    @Operation(summary = "Get issues in a project with filtering and pagination")
    public ResponseEntity<ApiResponse<PageResponse<IssueResponse>>> getProjectIssues(
            @PathVariable Long projectId,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssuePriority priority,
            @RequestParam(required = false) IssueType type,
            @RequestParam(required = false) Long assigneeId,
            @org.springdoc.core.annotations.ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<IssueResponse> response = issueService.getProjectIssues(
                projectId, status, priority, type, assigneeId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/issues/{issueId}")
    @Operation(summary = "Get issue details by ID")
    public ResponseEntity<ApiResponse<IssueResponse>> getIssueById(@PathVariable Long issueId) {
        IssueResponse response = issueService.getIssueById(issueId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/api/v1/issues/{issueId}")
    @Operation(summary = "Update issue details (title, description, type, priority)")
    public ResponseEntity<ApiResponse<IssueResponse>> updateIssue(
            @PathVariable Long issueId,
            @Valid @RequestBody UpdateIssueRequest request) {
        IssueResponse response = issueService.updateIssue(issueId, request);
        return ResponseEntity.ok(ApiResponse.success("Issue updated successfully", response));
    }

    @PutMapping("/api/v1/issues/{issueId}/assignee")
    @Operation(summary = "Assign or reassign an issue to an eligible project member")
    public ResponseEntity<ApiResponse<IssueResponse>> assignIssue(
            @PathVariable Long issueId,
            @RequestBody AssignIssueRequest request) {
        Long assigneeId = request != null ? request.getAssigneeId() : null;
        IssueResponse response = issueService.assignIssue(issueId, assigneeId);
        return ResponseEntity.ok(ApiResponse.success("Issue assignee updated successfully", response));
    }

    @PatchMapping("/api/v1/issues/{issueId}/status")
    @Operation(summary = "Transition issue status according to workflow rules")
    public ResponseEntity<ApiResponse<IssueResponse>> transitionStatus(
            @PathVariable Long issueId,
            @Valid @RequestBody TransitionStatusRequest request) {
        IssueResponse response = issueService.transitionStatus(issueId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Issue status transitioned successfully", response));
    }

    @PostMapping("/api/v1/issues/{issueId}/reopen")
    @Operation(summary = "Reopen an issue (accessible to any active project member, PM, or Admin)")
    public ResponseEntity<ApiResponse<IssueResponse>> reopenIssue(
            @PathVariable Long issueId,
            @RequestBody(required = false) ReopenIssueRequest request) {
        String reason = request != null ? request.getReason() : null;
        IssueResponse response = issueService.reopenIssue(issueId, reason);
        return ResponseEntity.ok(ApiResponse.success("Issue reopened successfully", response));
    }

    @PostMapping("/api/v1/issues/{issueId}/archive")
    @Operation(summary = "Archive an issue (Admin or PM only)")
    public ResponseEntity<ApiResponse<Void>> archiveIssue(@PathVariable Long issueId) {
        issueService.archiveIssue(issueId);
        return ResponseEntity.ok(ApiResponse.success("Issue archived successfully", null));
    }

    @DeleteMapping("/api/v1/issues/{issueId}")
    @Operation(summary = "Soft delete / archive an issue (Admin or PM only)")
    public ResponseEntity<Void> deleteIssue(@PathVariable Long issueId) {
        issueService.archiveIssue(issueId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/issues/{issueId}/history")
    @Operation(summary = "Get audit history for an issue")
    public ResponseEntity<ApiResponse<List<IssueHistoryResponse>>> getIssueHistory(@PathVariable Long issueId) {
        // verify issue access first
        issueService.getIssueById(issueId);
        List<IssueHistoryResponse> history = historyService.getHistoryForIssue(issueId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}

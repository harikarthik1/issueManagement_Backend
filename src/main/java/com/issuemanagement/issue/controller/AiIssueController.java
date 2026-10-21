package com.issuemanagement.issue.controller;

import com.issuemanagement.common.dto.ApiResponse;
import com.issuemanagement.issue.dto.ai.AiIssueDraftResponse;
import com.issuemanagement.issue.dto.ai.SearchSimilarIssuesRequest;
import com.issuemanagement.issue.dto.ai.SimilarIssueResult;
import com.issuemanagement.issue.service.AiIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/issues/ai")
@RequiredArgsConstructor
@Tag(name = "AI Issue Assistant", description = "Endpoints for AI-assisted duplicate issue reporting and structured issue drafting")
public class AiIssueController {

    private final AiIssueService aiIssueService;

    @PostMapping("/search-similar")
    @Operation(summary = "Find similar issues within the project using Qdrant embeddings")
    public ResponseEntity<ApiResponse<List<SimilarIssueResult>>> searchSimilarIssues(
            @PathVariable Long projectId,
            @Valid @RequestBody SearchSimilarIssuesRequest request) {
        List<SimilarIssueResult> results = aiIssueService.searchSimilarIssues(projectId, request.description());
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PostMapping("/draft")
    @Operation(summary = "Draft a structured issue ticket from natural language using AI")
    public ResponseEntity<ApiResponse<AiIssueDraftResponse>> draftIssue(
            @PathVariable Long projectId,
            @Valid @RequestBody SearchSimilarIssuesRequest request) {
        AiIssueDraftResponse draft = aiIssueService.draftIssue(projectId, request.description());
        return ResponseEntity.ok(ApiResponse.success(draft));
    }

    @PostMapping("/sync-vectors")
    @Operation(summary = "Synchronize existing project issues to Qdrant vector store")
    public ResponseEntity<ApiResponse<String>> syncProjectVectors(@PathVariable Long projectId) {
        int count = aiIssueService.syncProjectIssues(projectId);
        return ResponseEntity.ok(ApiResponse.success("Successfully indexed " + count + " issues into Qdrant", null));
    }
}

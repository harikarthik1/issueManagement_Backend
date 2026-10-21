package com.issuemanagement.comment.controller;

import com.issuemanagement.comment.dto.CommentResponse;
import com.issuemanagement.comment.dto.CreateCommentRequest;
import com.issuemanagement.comment.dto.UpdateCommentRequest;
import com.issuemanagement.comment.service.CommentService;
import com.issuemanagement.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Endpoints for managing comments on issues")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/api/v1/issues/{issueId}/comments")
    @Operation(summary = "Add a comment to an issue")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable Long issueId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentService.addComment(issueId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Comment added successfully", response));
    }

    @GetMapping("/api/v1/issues/{issueId}/comments")
    @Operation(summary = "Get all comments for an issue")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getCommentsForIssue(@PathVariable Long issueId) {
        List<CommentResponse> comments = commentService.getCommentsForIssue(issueId);
        return ResponseEntity.ok(ApiResponse.success(comments));
    }

    @PatchMapping("/api/v1/comments/{commentId}")
    @Operation(summary = "Update a comment (Author or Admin only)")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        CommentResponse response = commentService.updateComment(commentId, request);
        return ResponseEntity.ok(ApiResponse.success("Comment updated successfully", response));
    }

    @DeleteMapping("/api/v1/comments/{commentId}")
    @Operation(summary = "Delete a comment (Author, PM, or Admin)")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }
}

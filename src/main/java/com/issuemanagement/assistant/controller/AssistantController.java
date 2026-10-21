package com.issuemanagement.assistant.controller;

import com.issuemanagement.assistant.cache.SemanticCacheMetrics;
import com.issuemanagement.assistant.dto.*;
import com.issuemanagement.assistant.service.AssistantConversationService;
import com.issuemanagement.assistant.service.AssistantOrchestrationService;
import com.issuemanagement.common.dto.ApiResponse;
import com.issuemanagement.identity.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Centralized AI Assistant", description = "Endpoints for AI assistant conversations, tool calling, and temporary memory")
public class AssistantController {

    private final AssistantConversationService conversationService;
    private final AssistantOrchestrationService orchestrationService;
    private final SemanticCacheMetrics cacheMetrics;

    public AssistantController(
            AssistantConversationService conversationService,
            AssistantOrchestrationService orchestrationService,
            SemanticCacheMetrics cacheMetrics) {
        this.conversationService = conversationService;
        this.orchestrationService = orchestrationService;
        this.cacheMetrics = cacheMetrics;
    }

    @PostMapping("/conversations")
    @Operation(summary = "Create a new assistant conversation for the current login session")
    public ResponseEntity<ApiResponse<ConversationSummaryResponse>> createConversation(
            @RequestBody(required = false) CreateConversationRequest request) {
        String title = (request != null) ? request.title() : null;
        ConversationSummaryResponse summary = conversationService.createConversation(title);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Conversation created successfully", summary));
    }

    @GetMapping("/conversations")
    @Operation(summary = "List all active assistant conversations for the current login session")
    public ResponseEntity<ApiResponse<List<ConversationSummaryResponse>>> listConversations() {
        List<ConversationSummaryResponse> summaries = conversationService.listConversationsForSession();
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Retrieve ordered messages for an authorized conversation")
    public ResponseEntity<ApiResponse<List<AssistantMessageDto>>> getConversationMessages(
            @PathVariable String conversationId) {
        List<AssistantMessageDto> messages = conversationService.getConversationMessages(conversationId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Send a question to the assistant and receive a grounded response")
    public ResponseEntity<ApiResponse<AssistantChatResponse>> sendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody AssistantChatMessageRequest request) {
        AssistantChatResponse response = orchestrationService.processUserMessage(conversationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "Delete an assistant conversation and purge its temporary messages")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable String conversationId) {
        conversationService.deleteConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted successfully", null));
    }

    @DeleteMapping("/session")
    @Operation(summary = "Clear all assistant conversations for the current login session")
    public ResponseEntity<ApiResponse<Void>> clearSessionConversations() {
        String sessionId = SecurityUtils.getCurrentSessionId();
        conversationService.deleteAllConversationsForSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Session conversations cleared successfully", null));
    }

    @GetMapping("/cache/metrics")
    @Operation(summary = "Get operational metrics for assistant semantic caching")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCacheMetrics() {
        return ResponseEntity.ok(ApiResponse.success(cacheMetrics.getMetricsSnapshot()));
    }
}

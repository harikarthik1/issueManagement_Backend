package com.issuemanagement.assistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.issuemanagement.assistant.cache.SemanticCacheMetrics;
import com.issuemanagement.assistant.dto.*;
import com.issuemanagement.assistant.service.AssistantConversationService;
import com.issuemanagement.assistant.service.AssistantOrchestrationService;
import com.issuemanagement.common.exception.GlobalExceptionHandler;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssistantControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private AssistantConversationService conversationService;

    @Mock
    private AssistantOrchestrationService orchestrationService;

    @Mock
    private SemanticCacheMetrics cacheMetrics;

    @InjectMocks
    private AssistantController assistantController;

    private static final String CONVERSATION_ID = "conv-test-123";
    private static final String SESSION_ID = "session-test-456";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(assistantController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .email("user@test.com")
                .role(Role.EMPLOYEE)
                .active(true)
                .sessionId(SESSION_ID)
                .authorities(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/assistant/conversations creates a conversation")
    void testCreateConversation() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest("Project Q&A");
        ConversationSummaryResponse response = new ConversationSummaryResponse(
                CONVERSATION_ID, "Project Q&A", Instant.now(), Instant.now(), 0);

        when(conversationService.createConversation(eq("Project Q&A"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/assistant/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(CONVERSATION_ID))
                .andExpect(jsonPath("$.data.title").value("Project Q&A"));
    }

    @Test
    @DisplayName("GET /api/v1/assistant/conversations lists conversations for current session")
    void testListConversations() throws Exception {
        ConversationSummaryResponse c1 = new ConversationSummaryResponse(
                "c1", "Chat 1", Instant.now(), Instant.now(), 2);
        ConversationSummaryResponse c2 = new ConversationSummaryResponse(
                "c2", "Chat 2", Instant.now(), Instant.now(), 4);

        when(conversationService.listConversationsForSession()).thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/api/v1/assistant/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("c1"));
    }

    @Test
    @DisplayName("GET /api/v1/assistant/conversations/{id}/messages retrieves ordered messages")
    void testGetConversationMessages() throws Exception {
        AssistantMessageDto m1 = new AssistantMessageDto("user", "What is the status of ALPHA-1?");
        AssistantMessageDto m2 = new AssistantMessageDto("assistant", "It is OPEN.", List.of("ALPHA-1"));

        when(conversationService.getConversationMessages(CONVERSATION_ID)).thenReturn(List.of(m1, m2));

        mockMvc.perform(get("/api/v1/assistant/conversations/" + CONVERSATION_ID + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"));
    }

    @Test
    @DisplayName("POST /api/v1/assistant/conversations/{id}/messages sends message and returns grounded response")
    void testSendMessage() throws Exception {
        AssistantChatMessageRequest request = new AssistantChatMessageRequest("Why was ALPHA-1 reopened?");
        AssistantChatResponse chatResponse = new AssistantChatResponse(
                CONVERSATION_ID,
                "ALPHA-1 was reopened because the login defect reproduced after deployment.",
                List.of("ALPHA-1"),
                false
        );

        when(orchestrationService.processUserMessage(eq(CONVERSATION_ID), any(AssistantChatMessageRequest.class)))
                .thenReturn(chatResponse);

        mockMvc.perform(post("/api/v1/assistant/conversations/" + CONVERSATION_ID + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("ALPHA-1 was reopened")))
                .andExpect(jsonPath("$.data.referencedIssueKeys[0]").value("ALPHA-1"))
                .andExpect(jsonPath("$.data.cached").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/assistant/conversations/{id}/messages validates blank query")
    void testSendMessage_BlankValidation() throws Exception {
        AssistantChatMessageRequest request = new AssistantChatMessageRequest("   ");

        mockMvc.perform(post("/api/v1/assistant/conversations/" + CONVERSATION_ID + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("DELETE /api/v1/assistant/conversations/{id} deletes conversation")
    void testDeleteConversation() throws Exception {
        mockMvc.perform(delete("/api/v1/assistant/conversations/" + CONVERSATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(conversationService).deleteConversation(CONVERSATION_ID);
    }

    @Test
    @DisplayName("DELETE /api/v1/assistant/session clears session conversations")
    void testClearSessionConversations() throws Exception {
        mockMvc.perform(delete("/api/v1/assistant/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(conversationService).deleteAllConversationsForSession(SESSION_ID);
    }

    @Test
    @DisplayName("GET /api/v1/assistant/cache/metrics returns cache metrics")
    void testGetCacheMetrics() throws Exception {
        when(cacheMetrics.getMetricsSnapshot()).thenReturn(Map.of(
                "cacheHits", 5L,
                "cacheMisses", 10L,
                "geminiCallsAvoided", 5L
        ));

        mockMvc.perform(get("/api/v1/assistant/cache/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cacheHits").value(5))
                .andExpect(jsonPath("$.data.cacheMisses").value(10));
    }
}

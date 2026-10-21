package com.issuemanagement.assistant.service;

import com.issuemanagement.assistant.cache.SemanticCacheEntry;
import com.issuemanagement.assistant.cache.SemanticCacheService;
import com.issuemanagement.assistant.dto.AssistantChatMessageRequest;
import com.issuemanagement.assistant.dto.AssistantChatResponse;
import com.issuemanagement.assistant.dto.AssistantMessageDto;
import com.issuemanagement.assistant.exception.AssistantException;
import com.issuemanagement.assistant.model.ConversationMetadata;
import com.issuemanagement.assistant.tools.IssueAssistantTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantOrchestrationServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private AssistantConversationService conversationService;

    @Mock
    private SemanticCacheService semanticCacheService;

    @Mock
    private IssueAssistantTools issueAssistantTools;

    @InjectMocks
    private AssistantOrchestrationService orchestrationService;

    private static final String CONVERSATION_ID = "conv-orch-1";

    @BeforeEach
    void setUp() {
        ConversationMetadata metadata = ConversationMetadata.builder()
                .id(CONVERSATION_ID)
                .title("Test Chat")
                .userId(1L)
                .sessionId("session-orch-1")
                .build();
        lenient().when(conversationService.getConversationMetadata(CONVERSATION_ID)).thenReturn(metadata);
    }

    @Test
    void testProcessUserMessage_SemanticCacheHit_BypassesGemini() {
        String query = "How does issue priority work?";
        AssistantChatMessageRequest request = new AssistantChatMessageRequest(query);

        SemanticCacheEntry cached = SemanticCacheEntry.builder()
                .id("cached-1")
                .originalQuery(query)
                .response("Priorities are LOW, MEDIUM, HIGH, and CRITICAL.")
                .referencedIssueKeys(List.of())
                .build();

        when(semanticCacheService.findSimilarCachedResponse(query)).thenReturn(Optional.of(cached));

        AssistantChatResponse response = orchestrationService.processUserMessage(CONVERSATION_ID, request);

        assertThat(response.cached()).isTrue();
        assertThat(response.answer()).isEqualTo("Priorities are LOW, MEDIUM, HIGH, and CRITICAL.");
        assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);

        // Verify messages were appended to conversation memory
        verify(conversationService, times(2)).appendMessage(eq(CONVERSATION_ID), any(AssistantMessageDto.class));

        // Verify Gemini was NOT called!
        verifyNoInteractions(chatClient);
    }

    @Test
    void testProcessUserMessage_CacheMiss_InvokesGeminiWithTools() {
        String query = "What is the status of PROJ-101?";
        AssistantChatMessageRequest request = new AssistantChatMessageRequest(query);

        when(semanticCacheService.findSimilarCachedResponse(query)).thenReturn(Optional.empty());
        when(conversationService.getConversationMessages(CONVERSATION_ID)).thenReturn(List.of(
                new AssistantMessageDto("user", "Hello"),
                new AssistantMessageDto("assistant", "Hi! How can I help?")
        ));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Issue PROJ-101 is currently OPEN and assigned to Alice.");

        AssistantChatResponse response = orchestrationService.processUserMessage(CONVERSATION_ID, request);

        assertThat(response.cached()).isFalse();
        assertThat(response.answer()).contains("PROJ-101 is currently OPEN");
        assertThat(response.referencedIssueKeys()).contains("PROJ-101");

        verify(conversationService, times(2)).appendMessage(eq(CONVERSATION_ID), any(AssistantMessageDto.class));
        verify(semanticCacheService).cacheResponse(eq(query), contains("PROJ-101"), eq(List.of("PROJ-101")));
    }

    @Test
    void testProcessUserMessage_GeminiFailure_ThrowsAssistantException() {
        String query = "What is the status of PROJ-101?";
        AssistantChatMessageRequest request = new AssistantChatMessageRequest(query);

        when(semanticCacheService.findSimilarCachedResponse(query)).thenReturn(Optional.empty());
        when(conversationService.getConversationMessages(CONVERSATION_ID)).thenReturn(List.of());

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("Gemini quota exceeded"));

        assertThatThrownBy(() -> orchestrationService.processUserMessage(CONVERSATION_ID, request))
                .isInstanceOf(AssistantException.class)
                .hasMessageContaining("temporarily unable to generate a response");

        // Verify failed response was not saved to Redis
        verify(conversationService, never()).appendMessage(anyString(), any(AssistantMessageDto.class));
    }

    @Test
    void testProcessUserMessage_OffTopicCodeRequest_RefusesWithoutGemini() {
        String query = "write a python code to parse csv files";
        AssistantChatMessageRequest request = new AssistantChatMessageRequest(query);

        AssistantChatResponse response = orchestrationService.processUserMessage(CONVERSATION_ID, request);

        assertThat(response.cached()).isFalse();
        assertThat(response.answer()).isEqualTo(AssistantOrchestrationService.OFF_TOPIC_REFUSAL_MESSAGE);
        assertThat(response.referencedIssueKeys()).isEmpty();

        // Verify conversation was recorded
        verify(conversationService, times(2)).appendMessage(eq(CONVERSATION_ID), any(AssistantMessageDto.class));

        // Verify Gemini and Semantic Cache were NOT invoked
        verifyNoInteractions(chatClient);
        verifyNoInteractions(semanticCacheService);
    }
}

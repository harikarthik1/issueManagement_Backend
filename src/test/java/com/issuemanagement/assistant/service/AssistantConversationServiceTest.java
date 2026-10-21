package com.issuemanagement.assistant.service;

import com.issuemanagement.assistant.dto.AssistantMessageDto;
import com.issuemanagement.assistant.dto.ConversationSummaryResponse;
import com.issuemanagement.assistant.exception.AssistantStorageException;
import com.issuemanagement.assistant.model.ConversationMetadata;
import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.common.exception.ResourceNotFoundException;
import com.issuemanagement.identity.entity.Role;
import com.issuemanagement.identity.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantConversationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private SetOperations<String, Object> setOps;

    @Mock
    private ListOperations<String, Object> listOps;

    @InjectMocks
    private AssistantConversationService conversationService;

    private static final Long USER_ID = 1L;
    private static final String SESSION_ID = "session-123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(conversationService, "conversationTtlSeconds", 86400L);
        ReflectionTestUtils.setField(conversationService, "maxMessagesPerConversation", 30);

        UserPrincipal principal = UserPrincipal.builder()
                .id(USER_ID)
                .email("user@test.com")
                .role(Role.EMPLOYEE)
                .active(true)
                .sessionId(SESSION_ID)
                .authorities(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreateConversation_Success() {
        ConversationSummaryResponse response = conversationService.createConversation("Project Discussion");

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotBlank();
        assertThat(response.title()).isEqualTo("Project Discussion");
        assertThat(response.messageCount()).isZero();

        verify(valueOps).set(contains(response.id()), any(ConversationMetadata.class), any(Duration.class));
        verify(setOps).add(eq("assistant:session:" + SESSION_ID + ":conversations"), eq(response.id()));
    }

    @Test
    void testCreateConversation_DefaultTitle() {
        ConversationSummaryResponse response = conversationService.createConversation(null);

        assertThat(response.title()).isEqualTo("New Conversation");
    }

    @Test
    void testListConversationsForSession_MultipleIndependent() {
        String conv1 = "conv-1";
        String conv2 = "conv-2";
        Set<Object> convIds = Set.of(conv1, conv2);

        when(setOps.members("assistant:session:" + SESSION_ID + ":conversations")).thenReturn(convIds);

        ConversationMetadata meta1 = ConversationMetadata.builder()
                .id(conv1)
                .title("Conversation 1")
                .userId(USER_ID)
                .sessionId(SESSION_ID)
                .createdAt(Instant.now().minusSeconds(100))
                .updatedAt(Instant.now().minusSeconds(50))
                .messageCount(2)
                .build();

        ConversationMetadata meta2 = ConversationMetadata.builder()
                .id(conv2)
                .title("Conversation 2")
                .userId(USER_ID)
                .sessionId(SESSION_ID)
                .createdAt(Instant.now().minusSeconds(200))
                .updatedAt(Instant.now())
                .messageCount(5)
                .build();

        when(valueOps.get("assistant:conv:" + conv1 + ":meta")).thenReturn(meta1);
        when(valueOps.get("assistant:conv:" + conv2 + ":meta")).thenReturn(meta2);

        List<ConversationSummaryResponse> list = conversationService.listConversationsForSession();

        assertThat(list).hasSize(2);
        // Order by updatedAt DESC
        assertThat(list.get(0).id()).isEqualTo(conv2);
        assertThat(list.get(1).id()).isEqualTo(conv1);
    }

    @Test
    void testGetConversationMetadata_ForbiddenForDifferentUser() {
        String convId = "conv-cross-user";
        ConversationMetadata otherUserMeta = ConversationMetadata.builder()
                .id(convId)
                .title("Other's Chat")
                .userId(999L)
                .sessionId(SESSION_ID)
                .build();

        when(valueOps.get("assistant:conv:" + convId + ":meta")).thenReturn(otherUserMeta);

        assertThatThrownBy(() -> conversationService.getConversationMetadata(convId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void testGetConversationMetadata_ForbiddenForDifferentSession() {
        String convId = "conv-cross-session";
        ConversationMetadata otherSessionMeta = ConversationMetadata.builder()
                .id(convId)
                .title("Other Session Chat")
                .userId(USER_ID)
                .sessionId("different-session-456")
                .build();

        when(valueOps.get("assistant:conv:" + convId + ":meta")).thenReturn(otherSessionMeta);

        assertThatThrownBy(() -> conversationService.getConversationMetadata(convId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void testGetConversationMetadata_NotFound() {
        String convId = "missing-conv";
        when(valueOps.get("assistant:conv:" + convId + ":meta")).thenReturn(null);

        assertThatThrownBy(() -> conversationService.getConversationMetadata(convId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testGetConversationMessages_Ordered() {
        String convId = "conv-messages";
        ConversationMetadata meta = ConversationMetadata.builder()
                .id(convId)
                .title("Chat")
                .userId(USER_ID)
                .sessionId(SESSION_ID)
                .build();
        when(valueOps.get("assistant:conv:" + convId + ":meta")).thenReturn(meta);

        AssistantMessageDto m1 = new AssistantMessageDto("user", "Hello");
        AssistantMessageDto m2 = new AssistantMessageDto("assistant", "Hi there!");
        List<Object> rawList = List.of(m1, m2);

        when(listOps.range("assistant:conv:" + convId + ":messages", 0, -1)).thenReturn(rawList);

        List<AssistantMessageDto> messages = conversationService.getConversationMessages(convId);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content()).isEqualTo("Hello");
        assertThat(messages.get(1).content()).isEqualTo("Hi there!");
    }

    @Test
    void testAppendMessage_UpdatesMetadataAndTrims() {
        String convId = "conv-append";
        ConversationMetadata meta = ConversationMetadata.builder()
                .id(convId)
                .title("New Conversation")
                .userId(USER_ID)
                .sessionId(SESSION_ID)
                .messageCount(0)
                .build();
        when(valueOps.get("assistant:conv:" + convId + ":meta")).thenReturn(meta);
        when(listOps.size(anyString())).thenReturn(1L);

        AssistantMessageDto userMsg = new AssistantMessageDto("user", "What is the status of ISSUE-1?");
        conversationService.appendMessage(convId, userMsg);

        verify(listOps).rightPush(eq("assistant:conv:" + convId + ":messages"), eq(userMsg));
        verify(valueOps).set(eq("assistant:conv:" + convId + ":meta"), any(ConversationMetadata.class), any(Duration.class));
        assertThat(meta.getTitle()).isEqualTo("What is the status of ISSUE-1?");
    }

    @Test
    void testDeleteConversation_RemovesMessagesAndMeta() {
        String convId = "conv-delete";
        ConversationMetadata meta = ConversationMetadata.builder()
                .id(convId)
                .title("To Delete")
                .userId(USER_ID)
                .sessionId(SESSION_ID)
                .build();
        when(valueOps.get("assistant:conv:" + convId + ":meta")).thenReturn(meta);

        conversationService.deleteConversation(convId);

        verify(redisTemplate).delete(eq(List.of("assistant:conv:" + convId + ":meta", "assistant:conv:" + convId + ":messages")));
        verify(setOps).remove("assistant:session:" + SESSION_ID + ":conversations", convId);
    }

    @Test
    void testDeleteAllConversationsForSession_PurgesAllSessionKeys() {
        String sessionToPurge = "session-to-purge";
        Set<Object> convs = Set.of("conv-A", "conv-B");
        when(setOps.members("assistant:session:" + sessionToPurge + ":conversations")).thenReturn(convs);

        conversationService.deleteAllConversationsForSession(sessionToPurge);

        verify(redisTemplate).delete(argThat((List<String> list) ->
                list.contains("assistant:conv:conv-A:meta") &&
                list.contains("assistant:conv:conv-A:messages") &&
                list.contains("assistant:conv:conv-B:meta") &&
                list.contains("assistant:conv:conv-B:messages") &&
                list.contains("assistant:session:" + sessionToPurge + ":conversations")
        ));
    }

    @Test
    void testRedisOutage_ThrowsAssistantStorageException() {
        doThrow(new RedisConnectionFailureException("Connection refused"))
                .when(valueOps).set(anyString(), any(), any(Duration.class));

        assertThatThrownBy(() -> conversationService.createConversation("Test"))
                .isInstanceOf(AssistantStorageException.class)
                .hasMessageContaining("Failed to initialize assistant conversation storage");
    }
}

package com.issuemanagement.assistant.service;

import com.issuemanagement.assistant.dto.AssistantMessageDto;
import com.issuemanagement.assistant.dto.ConversationSummaryResponse;
import com.issuemanagement.assistant.exception.AssistantStorageException;
import com.issuemanagement.assistant.model.ConversationMetadata;
import com.issuemanagement.common.exception.ForbiddenException;
import com.issuemanagement.common.exception.ResourceNotFoundException;
import com.issuemanagement.identity.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AssistantConversationService {

    private final RedisTemplate<String, Object> assistantRedisTemplate;

    public AssistantConversationService(RedisTemplate<String, Object> assistantRedisTemplate) {
        this.assistantRedisTemplate = assistantRedisTemplate;
    }

    @Value("${assistant.memory.conversation-ttl-seconds:86400}")
    private long conversationTtlSeconds;

    @Value("${assistant.memory.max-messages-per-conversation:30}")
    private int maxMessagesPerConversation;

    private static final String SESSION_KEY_PREFIX = "assistant:session:";
    private static final String CONV_META_KEY_PREFIX = "assistant:conv:";
    private static final String CONV_MSGS_SUFFIX = ":messages";
    private static final String CONV_META_SUFFIX = ":meta";

    public ConversationSummaryResponse createConversation(String title) {
        Long userId = SecurityUtils.getCurrentUserId();
        String sessionId = SecurityUtils.getCurrentSessionId();
        String conversationId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String displayTitle = (title != null && !title.isBlank()) ? title.trim() : "New Conversation";
        ConversationMetadata metadata = ConversationMetadata.builder()
                .id(conversationId)
                .title(displayTitle)
                .userId(userId)
                .sessionId(sessionId)
                .createdAt(now)
                .updatedAt(now)
                .messageCount(0)
                .build();

        try {
            String metaKey = metaKey(conversationId);
            String sessionKey = sessionKey(sessionId);

            assistantRedisTemplate.opsForValue().set(metaKey, metadata, Duration.ofSeconds(conversationTtlSeconds));
            assistantRedisTemplate.opsForSet().add(sessionKey, conversationId);
            assistantRedisTemplate.expire(sessionKey, conversationTtlSeconds, TimeUnit.SECONDS);

            log.info("Created assistant conversation {} for session {} and user {}", conversationId, sessionId, userId);
            return toSummaryResponse(metadata);
        } catch (Exception ex) {
            log.error("Failed to create conversation in Redis for session {}", sessionId, ex);
            throw new AssistantStorageException("Failed to initialize assistant conversation storage", ex);
        }
    }

    public List<ConversationSummaryResponse> listConversationsForSession() {
        Long userId = SecurityUtils.getCurrentUserId();
        String sessionId = SecurityUtils.getCurrentSessionId();

        try {
            String sessionKey = sessionKey(sessionId);
            Set<Object> conversationIds = assistantRedisTemplate.opsForSet().members(sessionKey);
            if (conversationIds == null || conversationIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<ConversationSummaryResponse> summaries = new ArrayList<>();
            for (Object convIdObj : conversationIds) {
                String convId = convIdObj.toString();
                Object metaObj = assistantRedisTemplate.opsForValue().get(metaKey(convId));
                if (metaObj instanceof ConversationMetadata metadata) {
                    if (Objects.equals(metadata.getUserId(), userId) && Objects.equals(metadata.getSessionId(), sessionId)) {
                        summaries.add(toSummaryResponse(metadata));
                    }
                }
            }

            summaries.sort(Comparator.comparing(ConversationSummaryResponse::updatedAt).reversed());
            return summaries;
        } catch (Exception ex) {
            log.error("Failed to list conversations from Redis for session {}", sessionId, ex);
            throw new AssistantStorageException("Failed to retrieve conversations from storage", ex);
        }
    }

    public ConversationMetadata getConversationMetadata(String conversationId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentSessionId = SecurityUtils.getCurrentSessionId();

        try {
            Object metaObj = assistantRedisTemplate.opsForValue().get(metaKey(conversationId));
            if (!(metaObj instanceof ConversationMetadata metadata)) {
                throw new ResourceNotFoundException("Conversation", "id", conversationId);
            }

            if (!Objects.equals(metadata.getUserId(), currentUserId) || !Objects.equals(metadata.getSessionId(), currentSessionId)) {
                log.warn("Access denied: User {} / Session {} attempted to access conversation {} owned by User {} / Session {}",
                        currentUserId, currentSessionId, conversationId, metadata.getUserId(), metadata.getSessionId());
                throw new ForbiddenException("You do not have permission to access this conversation");
            }

            return metadata;
        } catch (ResourceNotFoundException | ForbiddenException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to fetch conversation metadata for {}", conversationId, ex);
            throw new AssistantStorageException("Failed to access conversation metadata in storage", ex);
        }
    }

    public List<AssistantMessageDto> getConversationMessages(String conversationId) {
        getConversationMetadata(conversationId); // Enforces ownership and existence

        try {
            String msgsKey = msgsKey(conversationId);
            List<Object> rawMessages = assistantRedisTemplate.opsForList().range(msgsKey, 0, -1);
            if (rawMessages == null || rawMessages.isEmpty()) {
                return Collections.emptyList();
            }

            List<AssistantMessageDto> messages = new ArrayList<>();
            for (Object obj : rawMessages) {
                if (obj instanceof AssistantMessageDto dto) {
                    messages.add(dto);
                }
            }
            return messages;
        } catch (Exception ex) {
            log.error("Failed to load messages for conversation {}", conversationId, ex);
            throw new AssistantStorageException("Failed to retrieve conversation messages from storage", ex);
        }
    }

    public void appendMessage(String conversationId, AssistantMessageDto message) {
        ConversationMetadata metadata = getConversationMetadata(conversationId); // Enforces ownership

        try {
            String msgsKey = msgsKey(conversationId);
            String metaKey = metaKey(conversationId);
            String sessionKey = sessionKey(metadata.getSessionId());

            assistantRedisTemplate.opsForList().rightPush(msgsKey, message);

            Long size = assistantRedisTemplate.opsForList().size(msgsKey);
            if (size != null && size > maxMessagesPerConversation) {
                assistantRedisTemplate.opsForList().trim(msgsKey, size - maxMessagesPerConversation, -1);
            }

            // Update title from first user query if still default
            if ("New Conversation".equalsIgnoreCase(metadata.getTitle()) && "user".equalsIgnoreCase(message.role())) {
                String userText = message.content();
                String generatedTitle = userText.length() > 50 ? userText.substring(0, 47) + "..." : userText;
                metadata.setTitle(generatedTitle.lines().findFirst().orElse("Conversation").trim());
            }

            metadata.setUpdatedAt(Instant.now());
            metadata.setMessageCount(size != null ? size.intValue() : metadata.getMessageCount() + 1);

            assistantRedisTemplate.opsForValue().set(metaKey, metadata, Duration.ofSeconds(conversationTtlSeconds));
            assistantRedisTemplate.expire(msgsKey, conversationTtlSeconds, TimeUnit.SECONDS);
            assistantRedisTemplate.expire(sessionKey, conversationTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.error("Failed to append message to conversation {}", conversationId, ex);
            throw new AssistantStorageException("Failed to save message to storage", ex);
        }
    }

    public void deleteConversation(String conversationId) {
        ConversationMetadata metadata = getConversationMetadata(conversationId); // Enforces ownership

        try {
            String metaKey = metaKey(conversationId);
            String msgsKey = msgsKey(conversationId);
            String sessionKey = sessionKey(metadata.getSessionId());

            assistantRedisTemplate.delete(List.of(metaKey, msgsKey));
            assistantRedisTemplate.opsForSet().remove(sessionKey, conversationId);

            log.info("Deleted conversation {} and purged associated messages", conversationId);
        } catch (Exception ex) {
            log.error("Failed to delete conversation {}", conversationId, ex);
            throw new AssistantStorageException("Failed to delete conversation from storage", ex);
        }
    }

    public void deleteAllConversationsForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        try {
            String sessionKey = sessionKey(sessionId);
            Set<Object> conversationIds = assistantRedisTemplate.opsForSet().members(sessionKey);

            if (conversationIds != null && !conversationIds.isEmpty()) {
                List<String> keysToDelete = new ArrayList<>();
                for (Object convIdObj : conversationIds) {
                    String convId = convIdObj.toString();
                    keysToDelete.add(metaKey(convId));
                    keysToDelete.add(msgsKey(convId));
                }
                keysToDelete.add(sessionKey);
                assistantRedisTemplate.delete(keysToDelete);
                log.info("Purged {} assistant conversations and associated messages for logged-out session {}",
                        conversationIds.size(), sessionId);
            } else {
                assistantRedisTemplate.delete(sessionKey);
            }
        } catch (Exception ex) {
            log.error("Failed to purge conversations for session {}", sessionId, ex);
            // In logout flow, log failure safely without interrupting logout
        }
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId + ":conversations";
    }

    private String metaKey(String conversationId) {
        return CONV_META_KEY_PREFIX + conversationId + CONV_META_SUFFIX;
    }

    private String msgsKey(String conversationId) {
        return CONV_META_KEY_PREFIX + conversationId + CONV_MSGS_SUFFIX;
    }

    private ConversationSummaryResponse toSummaryResponse(ConversationMetadata metadata) {
        return new ConversationSummaryResponse(
                metadata.getId(),
                metadata.getTitle(),
                metadata.getCreatedAt(),
                metadata.getUpdatedAt(),
                metadata.getMessageCount()
        );
    }
}

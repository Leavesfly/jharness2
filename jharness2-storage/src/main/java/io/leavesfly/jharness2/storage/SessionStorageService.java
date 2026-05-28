package io.leavesfly.jharness2.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import io.leavesfly.jharness2.storage.entity.ChatMessageEntity;
import io.leavesfly.jharness2.storage.entity.SessionEntity;
import io.leavesfly.jharness2.storage.repository.ChatMessageRepository;
import io.leavesfly.jharness2.storage.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SessionStorageService implements SessionPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(SessionStorageService.class);
    private final SessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    public SessionStorageService(SessionRepository sessionRepository,
                                  ChatMessageRepository chatMessageRepository,
                                  ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
    }

    public void saveSession(String userId, String sessionId, String model,
                           List<?> messages, long inputTokens, long outputTokens) {
        Instant now = Instant.now();
        SessionEntity entity = sessionRepository.findByUserIdAndSessionId(userId, sessionId)
                .orElseGet(() -> {
                    SessionEntity newEntity = new SessionEntity();
                    newEntity.setUserId(userId);
                    newEntity.setSessionId(sessionId);
                    newEntity.setCreatedAt(now);
                    return newEntity;
                });

        entity.setModel(model);
        entity.setMessageCount(messages.size());
        entity.setInputTokens(inputTokens);
        entity.setOutputTokens(outputTokens);
        entity.setUpdatedAt(now);

        try {
            entity.setMessagesJson(objectMapper.writeValueAsString(messages));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize messages for session {}", sessionId, e);
            throw new RuntimeException("Failed to serialize messages", e);
        }

        sessionRepository.save(entity);

        // 双写：同步将消息拆分写入 chat_messages 表
        dualWriteMessages(userId, sessionId, messages, now);
    }

    /**
     * 将消息列表增量写入 chat_messages 表。
     * 仅写入比当前最大 seqNo 更新的消息，实现增量双写。
     */
    private void dualWriteMessages(String userId, String sessionId, List<?> messages, Instant now) {
        try {
            int currentMaxSeqNo = chatMessageRepository.findMaxSeqNo(userId, sessionId);
            int startIndex = currentMaxSeqNo + 1;

            if (startIndex >= messages.size()) {
                return; // 没有新消息需要写入
            }

            for (int i = startIndex; i < messages.size(); i++) {
                Object message = messages.get(i);
                String role = extractRole(message);
                String content = extractContent(message);

                ChatMessageEntity chatMessage = new ChatMessageEntity();
                chatMessage.setUserId(userId);
                chatMessage.setSessionId(sessionId);
                chatMessage.setSeqNo(i);
                chatMessage.setRole(role);
                chatMessage.setContent(content);
                chatMessage.setTokenCount(estimateTokenCount(content));
                chatMessage.setCreatedAt(now);
                chatMessageRepository.save(chatMessage);
            }

            logger.debug("Dual-write completed: session={}, newMessages={}", sessionId, messages.size() - startIndex);
        } catch (Exception e) {
            // 双写失败不阻塞主流程，仅记录警告
            logger.warn("Dual-write to chat_messages failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    private String extractRole(Object message) {
        try {
            JsonNode node = objectMapper.valueToTree(message);
            if (node.has("role")) {
                return node.get("role").asText("unknown");
            }
        } catch (Exception e) {
            logger.debug("Failed to extract role from message: {}", e.getMessage());
        }
        return "unknown";
    }

    private String extractContent(Object message) {
        try {
            JsonNode node = objectMapper.valueToTree(message);
            if (node.has("content")) {
                JsonNode contentNode = node.get("content");
                return contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
            }
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            return message.toString();
        }
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isEmpty()) return 0;
        // 简易估算：英文按4字符/token，中文按2字符/token，取中间值约3字符/token
        return Math.max(1, content.length() / 3);
    }

    public Optional<SessionEntity> findSession(String userId, String sessionId) {
        return sessionRepository.findByUserIdAndSessionId(userId, sessionId);
    }

    public List<SessionEntity> listSessions(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public void deleteSession(String userId, String sessionId) {
        sessionRepository.deleteByUserIdAndSessionId(userId, sessionId);
        chatMessageRepository.deleteBySession(userId, sessionId);
    }

    /**
     * 按分页加载会话消息（从 chat_messages 表读取）。
     */
    public List<ChatMessageEntity> loadMessages(String userId, String sessionId) {
        return chatMessageRepository.findBySession(userId, sessionId);
    }
}

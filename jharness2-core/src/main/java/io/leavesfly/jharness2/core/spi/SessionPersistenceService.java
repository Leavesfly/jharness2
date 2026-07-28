package io.leavesfly.jharness2.core.spi;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.util.List;
import java.util.Optional;

/**
 * SPI interface for session persistence.
 * Implementations are provided by the storage module.
 */
public interface SessionPersistenceService {

    void saveSession(String userId, String sessionId, String model,
                     List<?> messages, long inputTokens, long outputTokens);

    /**
     * 加载已持久化的会话快照(消息历史 + token 统计),用于引擎驱逐/重启后恢复上下文。
     * 默认实现返回 empty,保持向后兼容。
     */
    default Optional<PersistedSession> loadSession(String userId, String sessionId) {
        return Optional.empty();
    }

    /**
     * 持久化的会话快照。
     */
    record PersistedSession(List<ConversationMessage> messages,
                            long inputTokens,
                            long outputTokens) {}
}

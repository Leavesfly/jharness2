package io.leavesfly.jharness2.core.spi;

import java.util.List;

/**
 * SPI interface for session persistence.
 * Implementations are provided by the storage module.
 */
public interface SessionPersistenceService {

    void saveSession(String userId, String sessionId, String model,
                     List<?> messages, long inputTokens, long outputTokens);
}

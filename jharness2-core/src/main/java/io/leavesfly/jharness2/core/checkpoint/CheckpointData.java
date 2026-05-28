package io.leavesfly.jharness2.core.checkpoint;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.time.Instant;
import java.util.List;

/**
 * 引擎状态检查点数据。
 */
public class CheckpointData {

    private final String userId;
    private final String sessionId;
    private final List<ConversationMessage> messages;
    private final long inputTokens;
    private final long outputTokens;
    private final int turnCount;
    private final Instant createdAt;

    public CheckpointData(String userId, String sessionId,
                          List<ConversationMessage> messages,
                          long inputTokens, long outputTokens, int turnCount) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.messages = List.copyOf(messages);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.turnCount = turnCount;
        this.createdAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public List<ConversationMessage> getMessages() { return messages; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public int getTurnCount() { return turnCount; }
    public Instant getCreatedAt() { return createdAt; }

    public String getCacheKey() { return userId + ":" + sessionId; }
}

package io.leavesfly.jharness2.core.quota;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 单次请求的用量记录。
 */
public class UsageRecord {

    private final String userId;
    private final String sessionId;
    private final String model;
    private final long inputTokens;
    private final long outputTokens;
    private final Instant timestamp;

    public UsageRecord(String userId, String sessionId, String model,
                       long inputTokens, long outputTokens) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.timestamp = Instant.now();
    }

    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getTotalTokens() { return inputTokens + outputTokens; }
    public Instant getTimestamp() { return timestamp; }
    public LocalDate getDate() { return timestamp.atZone(java.time.ZoneId.systemDefault()).toLocalDate(); }
}

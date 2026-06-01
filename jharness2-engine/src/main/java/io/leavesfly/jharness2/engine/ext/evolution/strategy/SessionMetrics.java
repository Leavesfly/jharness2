package io.leavesfly.jharness2.engine.ext.evolution.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话执行指标 —— 记录一次会话的关键性能数据，用于经验提取和策略进化。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionMetrics {

    private String sessionId;
    private int turnsUsed;
    private int maxTurns;
    private long inputTokens;
    private long outputTokens;
    private long durationMs;
    private boolean taskCompleted;
    private List<String> toolSequence = new ArrayList<>();
    private int toolErrorCount;
    private int retryCount;
    private Instant startedAt;
    private Instant completedAt;

    public SessionMetrics() {}

    public SessionMetrics(String sessionId, int maxTurns) {
        this.sessionId = sessionId;
        this.maxTurns = maxTurns;
        this.startedAt = Instant.now();
    }

    public void recordToolCall(String toolName) {
        toolSequence.add(toolName);
    }

    public void recordToolError() {
        toolErrorCount++;
    }

    public void recordRetry() {
        retryCount++;
    }

    public void complete(boolean taskCompleted, long inputTokens, long outputTokens) {
        this.taskCompleted = taskCompleted;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }

    public void incrementTurns() {
        turnsUsed++;
    }

    // --- Getters and Setters ---

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public int getTurnsUsed() { return turnsUsed; }
    public void setTurnsUsed(int turnsUsed) { this.turnsUsed = turnsUsed; }

    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int maxTurns) { this.maxTurns = maxTurns; }

    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }

    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isTaskCompleted() { return taskCompleted; }
    public void setTaskCompleted(boolean taskCompleted) { this.taskCompleted = taskCompleted; }

    public List<String> getToolSequence() { return toolSequence; }
    public void setToolSequence(List<String> toolSequence) { this.toolSequence = toolSequence; }

    public int getToolErrorCount() { return toolErrorCount; }
    public void setToolErrorCount(int toolErrorCount) { this.toolErrorCount = toolErrorCount; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

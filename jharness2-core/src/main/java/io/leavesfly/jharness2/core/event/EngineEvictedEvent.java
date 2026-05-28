package io.leavesfly.jharness2.core.event;

/**
 * 引擎驱逐事件。
 */
public class EngineEvictedEvent extends EngineEvent {

    private final String reason;
    private final long totalInputTokens;
    private final long totalOutputTokens;

    public EngineEvictedEvent(String userId, String sessionId, String reason,
                              long totalInputTokens, long totalOutputTokens) {
        super(userId, sessionId);
        this.reason = reason;
        this.totalInputTokens = totalInputTokens;
        this.totalOutputTokens = totalOutputTokens;
    }

    public String getReason() { return reason; }
    public long getTotalInputTokens() { return totalInputTokens; }
    public long getTotalOutputTokens() { return totalOutputTokens; }

    @Override
    public String getEventType() { return "engine.evicted"; }
}

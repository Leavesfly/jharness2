package io.leavesfly.jharness2.core.event;

import java.time.Instant;

/**
 * 引擎事件基类 —— 所有引擎生命周期事件的父类。
 */
public abstract class EngineEvent {

    private final String userId;
    private final String sessionId;
    private final Instant timestamp;

    protected EngineEvent(String userId, String sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.timestamp = Instant.now();
    }

    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public Instant getTimestamp() { return timestamp; }
    public String getCacheKey() { return userId + ":" + sessionId; }

    public abstract String getEventType();
}

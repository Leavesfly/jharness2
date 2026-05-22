package io.leavesfly.jharness2.engine.heartbeat;

import java.time.Instant;

/**
 * 心跳事件，携带每次心跳触发时的上下文信息。
 */
public class HeartbeatEvent {

    private final Instant timestamp;
    private final long sequenceNumber;
    private final long intervalMs;

    public HeartbeatEvent(Instant timestamp, long sequenceNumber, long intervalMs) {
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
        this.intervalMs = intervalMs;
    }

    /** 心跳触发的时间戳 */
    public Instant getTimestamp() { return timestamp; }

    /** 自启动以来的第几次心跳（从 1 开始） */
    public long getSequenceNumber() { return sequenceNumber; }

    /** 心跳间隔（毫秒） */
    public long getIntervalMs() { return intervalMs; }

    @Override
    public String toString() {
        return "HeartbeatEvent{seq=" + sequenceNumber + ", interval=" + intervalMs + "ms, at=" + timestamp + "}";
    }
}

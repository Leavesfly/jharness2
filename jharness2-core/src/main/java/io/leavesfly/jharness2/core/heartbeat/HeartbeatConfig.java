package io.leavesfly.jharness2.core.heartbeat;

/**
 * 心跳服务配置。
 */
public class HeartbeatConfig {

    /** 默认心跳间隔：30 秒 */
    public static final long DEFAULT_INTERVAL_MS = 30_000L;

    /** 默认初始延迟：与间隔相同 */
    public static final long DEFAULT_INITIAL_DELAY_MS = 0L;

    private long intervalMs;
    private long initialDelayMs;
    private boolean enabled;

    public HeartbeatConfig() {
        this.intervalMs = DEFAULT_INTERVAL_MS;
        this.initialDelayMs = DEFAULT_INITIAL_DELAY_MS;
        this.enabled = true;
    }

    public HeartbeatConfig(long intervalMs, long initialDelayMs, boolean enabled) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("intervalMs must be positive, got: " + intervalMs);
        }
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException("initialDelayMs must be non-negative, got: " + initialDelayMs);
        }
        this.intervalMs = intervalMs;
        this.initialDelayMs = initialDelayMs;
        this.enabled = enabled;
    }

    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }

    public long getInitialDelayMs() { return initialDelayMs; }
    public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public static HeartbeatConfig disabled() {
        return new HeartbeatConfig(DEFAULT_INTERVAL_MS, DEFAULT_INITIAL_DELAY_MS, false);
    }

    public static HeartbeatConfig withInterval(long intervalMs) {
        return new HeartbeatConfig(intervalMs, 0, true);
    }
}

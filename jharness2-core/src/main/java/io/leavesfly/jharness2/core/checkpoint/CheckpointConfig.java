package io.leavesfly.jharness2.core.checkpoint;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Checkpoint 相关配置。
 */
@Component
@ConfigurationProperties(prefix = "jharness2.checkpoint")
public class CheckpointConfig {

    /** 是否启用自动 checkpoint */
    private boolean enabled = true;

    /** 每隔多少个 turn 自动保存一次 checkpoint */
    private int intervalTurns = 3;

    /** 定时 checkpoint 间隔（秒），0 表示仅按 turn 数触发 */
    private int intervalSeconds = 60;

    /** 每个 session 最多保留多少个 checkpoint */
    private int maxCheckpointsPerSession = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getIntervalTurns() { return intervalTurns; }
    public void setIntervalTurns(int intervalTurns) { this.intervalTurns = intervalTurns; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public int getMaxCheckpointsPerSession() { return maxCheckpointsPerSession; }
    public void setMaxCheckpointsPerSession(int maxCheckpointsPerSession) { this.maxCheckpointsPerSession = maxCheckpointsPerSession; }
}

package io.leavesfly.jharness2.core.distributed;

import java.time.Instant;

/**
 * 节点负载信息 —— 随心跳上报，供调度决策使用。
 */
public class NodeLoadInfo {

    private final String nodeId;
    private final int activeEngineCount;
    private final int maxEngineCapacity;
    private final double cpuUsagePercent;
    private final double memoryUsagePercent;
    private final boolean draining;
    private final Instant reportedAt;

    public NodeLoadInfo(String nodeId, int activeEngineCount, int maxEngineCapacity,
                        double cpuUsagePercent, double memoryUsagePercent, boolean draining) {
        this.nodeId = nodeId;
        this.activeEngineCount = activeEngineCount;
        this.maxEngineCapacity = maxEngineCapacity;
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsagePercent = memoryUsagePercent;
        this.draining = draining;
        this.reportedAt = Instant.now();
    }

    public String getNodeId() { return nodeId; }
    public int getActiveEngineCount() { return activeEngineCount; }
    public int getMaxEngineCapacity() { return maxEngineCapacity; }
    public double getCpuUsagePercent() { return cpuUsagePercent; }
    public double getMemoryUsagePercent() { return memoryUsagePercent; }
    public boolean isDraining() { return draining; }
    public Instant getReportedAt() { return reportedAt; }

    /**
     * 计算负载评分（0-100，越低越空闲）。
     */
    public double getLoadScore() {
        if (draining) {
            return 100.0;
        }
        double engineLoadRatio = maxEngineCapacity > 0
                ? (double) activeEngineCount / maxEngineCapacity
                : 0;
        // 综合评分：引擎占用 40%，CPU 30%，内存 30%
        return engineLoadRatio * 40 + cpuUsagePercent * 0.3 + memoryUsagePercent * 0.3;
    }

    /**
     * 是否有余量接受新引擎。
     */
    public boolean hasCapacity() {
        return !draining && activeEngineCount < maxEngineCapacity && getLoadScore() < 80;
    }
}

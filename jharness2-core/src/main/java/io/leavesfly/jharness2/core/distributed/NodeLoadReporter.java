package io.leavesfly.jharness2.core.distributed;

import io.leavesfly.jharness2.core.EngineConfig;
import io.leavesfly.jharness2.core.UserEngineRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

/**
 * 节点负载上报器 —— 收集本节点的负载信息并上报到 EngineStateStore。
 * <p>
 * 典型用法：作为 HeartbeatListener 注册到 HeartbeatService，随心跳周期性上报。
 */
public class NodeLoadReporter {

    private static final Logger logger = LoggerFactory.getLogger(NodeLoadReporter.class);

    private final String nodeId;
    private final UserEngineRegistry engineRegistry;
    private final EngineConfig engineConfig;
    private final NodeLoadStore nodeLoadStore;
    private volatile boolean draining = false;

    public NodeLoadReporter(String nodeId, UserEngineRegistry engineRegistry,
                            EngineConfig engineConfig, NodeLoadStore nodeLoadStore) {
        this.nodeId = nodeId;
        this.engineRegistry = engineRegistry;
        this.engineConfig = engineConfig;
        this.nodeLoadStore = nodeLoadStore;
    }

    /**
     * 收集并上报负载信息。
     */
    public void report() {
        try {
            NodeLoadInfo loadInfo = collectLoadInfo();
            nodeLoadStore.report(nodeId, loadInfo);
            logger.debug("Node load reported: nodeId={}, score={:.1f}, engines={}",
                    nodeId, loadInfo.getLoadScore(), loadInfo.getActiveEngineCount());
        } catch (Exception e) {
            logger.warn("Failed to report node load: {}", e.getMessage());
        }
    }

    /**
     * 收集本节点负载信息。
     */
    public NodeLoadInfo collectLoadInfo() {
        int activeEngines = (int) engineRegistry.activeEngineCount();
        int maxCapacity = engineConfig.getMaxTotalEngines();
        double cpuUsage = getCpuUsage();
        double memoryUsage = getMemoryUsage();

        return new NodeLoadInfo(nodeId, activeEngines, maxCapacity,
                cpuUsage, memoryUsage, draining);
    }

    public void setDraining(boolean draining) {
        this.draining = draining;
    }

    private double getCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double load = osBean.getSystemLoadAverage();
            int processors = osBean.getAvailableProcessors();
            return Math.min(100.0, (load / processors) * 100);
        } catch (Exception e) {
            return 0;
        }
    }

    private double getMemoryUsage() {
        try {
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            long used = memBean.getHeapMemoryUsage().getUsed();
            long max = memBean.getHeapMemoryUsage().getMax();
            return max > 0 ? (double) used / max * 100 : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}

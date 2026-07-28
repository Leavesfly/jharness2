package io.leavesfly.jharness2.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 引擎线程池配置 —— Agent 循环与工具执行使用独立的有界线程池,
 * 避免阻塞式工作负载占满 ForkJoinPool.commonPool 导致全服务饿死。
 */
@ConfigurationProperties(prefix = "jharness2.engine.pool")
public class EngineExecutorConfig {

    /** Agent ReAct 循环线程数(每个并发对话占用一个线程直到本轮结束) */
    private int agentPoolSize = 32;

    /** Agent 任务等待队列容量,超出后快速失败 */
    private int agentQueueCapacity = 64;

    /** 工具执行线程数(单次对话可能并行多个工具) */
    private int toolPoolSize = 64;

    /** 工具任务等待队列容量 */
    private int toolQueueCapacity = 256;

    public int getAgentPoolSize() { return agentPoolSize; }
    public void setAgentPoolSize(int agentPoolSize) { this.agentPoolSize = agentPoolSize; }
    public int getAgentQueueCapacity() { return agentQueueCapacity; }
    public void setAgentQueueCapacity(int agentQueueCapacity) { this.agentQueueCapacity = agentQueueCapacity; }
    public int getToolPoolSize() { return toolPoolSize; }
    public void setToolPoolSize(int toolPoolSize) { this.toolPoolSize = toolPoolSize; }
    public int getToolQueueCapacity() { return toolQueueCapacity; }
    public void setToolQueueCapacity(int toolQueueCapacity) { this.toolQueueCapacity = toolQueueCapacity; }
}

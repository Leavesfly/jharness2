package io.leavesfly.jharness2.engine;

import io.leavesfly.jharness2.engine.agent.AgentOrchestrator;
import io.leavesfly.jharness2.engine.cron.CronScheduler;
import io.leavesfly.jharness2.engine.mcp.McpManager;
import io.leavesfly.jharness2.engine.skill.SkillRegistry;
import io.leavesfly.jharness2.engine.task.BackgroundTaskManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 引擎上下文 — 承载 QueryEngine 的可选扩展子系统。
 * <p>
 * QueryEngine 只保留驱动 ReAct 循环的核心依赖（LLM、工具、权限、Hook、压缩、持久化），
 * 其余可选能力（Sub-Agent、Skill、MCP、Cron、后台任务等）通过 EngineContext 管理，
 * 使 QueryEngine 保持精简，同时支持按需组合扩展。
 */
public class EngineContext implements AutoCloseable {

    private volatile AgentOrchestrator agentOrchestrator;
    private volatile SkillRegistry skillRegistry;
    private volatile McpManager mcpManager;
    private volatile CronScheduler cronScheduler;
    private volatile BackgroundTaskManager backgroundTaskManager;

    private final Map<Class<?>, Object> extensions = new ConcurrentHashMap<>();

    public Optional<AgentOrchestrator> getAgentOrchestrator() {
        return Optional.ofNullable(agentOrchestrator);
    }

    public void setAgentOrchestrator(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    public Optional<SkillRegistry> getSkillRegistry() {
        return Optional.ofNullable(skillRegistry);
    }

    public void setSkillRegistry(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public Optional<McpManager> getMcpManager() {
        return Optional.ofNullable(mcpManager);
    }

    public void setMcpManager(McpManager mcpManager) {
        this.mcpManager = mcpManager;
    }

    public Optional<CronScheduler> getCronScheduler() {
        return Optional.ofNullable(cronScheduler);
    }

    public void setCronScheduler(CronScheduler cronScheduler) {
        this.cronScheduler = cronScheduler;
    }

    public Optional<BackgroundTaskManager> getBackgroundTaskManager() {
        return Optional.ofNullable(backgroundTaskManager);
    }

    public void setBackgroundTaskManager(BackgroundTaskManager backgroundTaskManager) {
        this.backgroundTaskManager = backgroundTaskManager;
    }

    /**
     * 注册自定义扩展组件（SPI 扩展点）。
     */
    @SuppressWarnings("unchecked")
    public <T> void registerExtension(Class<T> type, T instance) {
        extensions.put(type, instance);
    }

    /**
     * 获取自定义扩展组件。
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getExtension(Class<T> type) {
        return Optional.ofNullable((T) extensions.get(type));
    }

    @Override
    public void close() {
        if (mcpManager != null) mcpManager.close();
        if (agentOrchestrator != null) agentOrchestrator.shutdown();
        if (backgroundTaskManager != null) backgroundTaskManager.shutdown();
        if (cronScheduler != null) cronScheduler.shutdown();
    }
}

package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.spi.TaskStateStore;
import io.leavesfly.jharness2.engine.EngineContext;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.ext.agent.AgentOrchestrator;
import io.leavesfly.jharness2.engine.ext.cron.CronScheduler;
import io.leavesfly.jharness2.engine.ext.task.BackgroundTaskManager;
import io.leavesfly.jharness2.engine.tool.builtin.meta.SubAgentTool;

import java.nio.file.Path;

/**
 * Agent/扩展子系统定制器 —— 初始化 Sub-Agent、Cron、后台任务。
 */
public class AgentCustomizer implements EngineCustomizer {

    private TaskStateStore taskStateStore;

    public AgentCustomizer() {
    }

    public AgentCustomizer(TaskStateStore taskStateStore) {
        this.taskStateStore = taskStateStore;
    }

    public void setTaskStateStore(TaskStateStore taskStateStore) {
        this.taskStateStore = taskStateStore;
    }

    @Override
    public void customize(QueryEngine engine, UserContext context, Path workspace) {
        EngineContext engineContext = engine.getEngineContext();
        if (engineContext == null) {
            engineContext = new EngineContext();
            engine.setEngineContext(engineContext);
        }

        // Sub-Agent 协调器
        AgentOrchestrator agentOrchestrator = new AgentOrchestrator(engine.getLlmClient());
        engineContext.setAgentOrchestrator(agentOrchestrator);
        engine.getToolRegistry().register(new SubAgentTool(agentOrchestrator));

        // Cron 调度器
        CronScheduler cronScheduler = new CronScheduler();
        engineContext.setCronScheduler(cronScheduler);

        // 后台任务管理
        Path taskOutputDir = workspace.resolve(".jharness/task-output");
        BackgroundTaskManager taskManager = new BackgroundTaskManager(taskOutputDir);

        // 注入任务状态持久化桥接
        if (taskStateStore != null) {
            String userId = context.getUserId();
            String sessionId = context.getSessionId();
            taskManager.setLifecycleListener(new TaskStateBridge(taskStateStore, userId, sessionId));
        }

        engineContext.setBackgroundTaskManager(taskManager);
    }

    @Override
    public int getOrder() { return 400; }
}

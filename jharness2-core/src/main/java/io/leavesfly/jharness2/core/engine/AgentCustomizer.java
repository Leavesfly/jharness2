package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.spi.TaskState;
import io.leavesfly.jharness2.core.spi.TaskStateStore;
import io.leavesfly.jharness2.core.spi.TaskStatus;
import io.leavesfly.jharness2.engine.EngineContext;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.ext.agent.AgentOrchestrator;
import io.leavesfly.jharness2.engine.ext.cron.CronScheduler;
import io.leavesfly.jharness2.engine.ext.task.BackgroundTaskManager;
import io.leavesfly.jharness2.engine.tool.builtin.meta.SubAgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Agent/扩展子系统定制器 —— 初始化 Sub-Agent、Cron、后台任务。
 */
public class AgentCustomizer implements EngineCustomizer {

    private static final Logger logger = LoggerFactory.getLogger(AgentCustomizer.class);

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
            // 新引擎意味着旧引擎的后台进程已失控：把该 session 残留的 RUNNING 任务
            // 标记为 FAILED，消除“任务还在跑”的假象
            markOrphanedTasks(userId, sessionId);
        }

        engineContext.setBackgroundTaskManager(taskManager);
    }

    /**
     * 引擎重建（驱逐后重建/服务重启）时，上一代引擎启动的后台任务进程已无人追踪，
     * 将其持久化状态从 RUNNING 改为 FAILED，避免用户查询到永远“运行中”的僵尸任务。
     */
    private void markOrphanedTasks(String userId, String sessionId) {
        try {
            for (TaskState task : taskStateStore.findActiveTasks(userId)) {
                if (sessionId.equals(task.sessionId()) && task.status() == TaskStatus.RUNNING) {
                    taskStateStore.updateStatus(task.taskId(), TaskStatus.FAILED,
                            "Task orphaned by engine restart");
                    logger.info("Marked orphaned task as FAILED: taskId={}, user={}, session={}",
                            task.taskId(), userId, sessionId);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to mark orphaned tasks: user={}, session={}, error={}",
                    userId, sessionId, e.getMessage());
        }
    }

    @Override
    public int getOrder() { return 400; }
}

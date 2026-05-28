package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.spi.TaskState;
import io.leavesfly.jharness2.core.spi.TaskStateStore;
import io.leavesfly.jharness2.core.spi.TaskStatus;
import io.leavesfly.jharness2.engine.ext.task.BackgroundTask;
import io.leavesfly.jharness2.engine.ext.task.TaskLifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * 桥接 engine 层 BackgroundTaskManager 的生命周期事件到 core 层 TaskStateStore SPI。
 * <p>
 * 在 AgentCustomizer 中创建并注入到 BackgroundTaskManager，实现任务状态的自动持久化。
 */
public class TaskStateBridge implements TaskLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(TaskStateBridge.class);

    private final TaskStateStore taskStateStore;
    private final String userId;
    private final String sessionId;

    public TaskStateBridge(TaskStateStore taskStateStore, String userId, String sessionId) {
        this.taskStateStore = taskStateStore;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    @Override
    public void onTaskCreated(BackgroundTask task) {
        Instant now = Instant.now();
        Instant timeoutAt = now.plusSeconds(3600); // 默认1小时超时
        TaskState state = new TaskState(
                task.getTaskId(),
                userId,
                sessionId,
                null,
                TaskState.TYPE_BACKGROUND,
                TaskStatus.RUNNING,
                task.getCommand(),
                null,
                0,
                3,
                now,
                now,
                timeoutAt
        );
        try {
            taskStateStore.create(state);
        } catch (Exception e) {
            logger.warn("Failed to persist task creation: taskId={}, error={}", task.getTaskId(), e.getMessage());
        }
    }

    @Override
    public void onTaskStatusChanged(BackgroundTask task) {
        TaskStatus status = mapStatus(task.getStatus());
        try {
            taskStateStore.updateStatus(task.getTaskId(), status, task.getOutput());
        } catch (Exception e) {
            logger.warn("Failed to persist task status change: taskId={}, status={}, error={}",
                    task.getTaskId(), status, e.getMessage());
        }
    }

    private TaskStatus mapStatus(BackgroundTask.Status engineStatus) {
        return switch (engineStatus) {
            case RUNNING -> TaskStatus.RUNNING;
            case COMPLETED -> TaskStatus.COMPLETED;
            case FAILED -> TaskStatus.FAILED;
            case CANCELLED -> TaskStatus.CANCELLED;
        };
    }
}

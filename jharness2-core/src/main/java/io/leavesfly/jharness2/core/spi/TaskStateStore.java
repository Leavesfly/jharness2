package io.leavesfly.jharness2.core.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 后台任务/Sub-Agent 任务状态持久化 SPI。
 * <p>
 * 支持任务状态追踪、故障恢复、超时检测。
 */
public interface TaskStateStore {

    /** 创建任务记录 */
    void create(TaskState task);

    /** 更新任务状态 */
    void updateStatus(String taskId, TaskStatus status, String output);

    /** 查询任务 */
    Optional<TaskState> findById(String taskId);

    /** 查询用户的活跃任务 */
    List<TaskState> findActiveTasks(String userId);

    /** 查询超时任务（用于恢复/清理） */
    List<TaskState> findTimedOutTasks(Instant timeoutBefore);

    /** 标记任务重试 */
    void incrementRetry(String taskId);
}

package io.leavesfly.jharness2.core.spi;

import java.time.Instant;

/**
 * 任务状态数据。
 */
public record TaskState(
    String taskId,
    String userId,
    String sessionId,
    String parentTaskId,
    String type,
    TaskStatus status,
    String input,
    String output,
    int retryCount,
    int maxRetries,
    Instant createdAt,
    Instant updatedAt,
    Instant timeoutAt
) {
    /** 任务类型常量 */
    public static final String TYPE_BACKGROUND = "BACKGROUND";
    public static final String TYPE_SUB_AGENT = "SUB_AGENT";
    public static final String TYPE_SCHEDULED = "SCHEDULED";
}

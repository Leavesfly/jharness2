package io.leavesfly.jharness2.core.spi;

/**
 * 任务状态枚举。
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}

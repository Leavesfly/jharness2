package io.leavesfly.jharness2.engine.ext.task;

/**
 * 后台任务生命周期监听器。
 * <p>
 * 由上层模块实现，用于在任务创建、完成、失败等事件时执行额外逻辑（如持久化）。
 * engine 层仅定义回调接口，不关心具体实现。
 */
public interface TaskLifecycleListener {

    /** 任务被提交时回调 */
    void onTaskCreated(BackgroundTask task);

    /** 任务状态变更时回调 */
    void onTaskStatusChanged(BackgroundTask task);
}

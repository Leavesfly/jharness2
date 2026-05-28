package io.leavesfly.jharness2.core;

/**
 * 引擎实例的生命周期状态。
 */
public enum EngineLifecycleState {

    /** 引擎正在运行，可接受新请求 */
    RUNNING,

    /** 引擎正在优雅关闭中：等待当前 turn 完成、持久化状态 */
    SHUTTING_DOWN,

    /** 引擎已完全关闭 */
    CLOSED
}

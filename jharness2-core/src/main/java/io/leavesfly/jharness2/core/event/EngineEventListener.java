package io.leavesfly.jharness2.core.event;

/**
 * 引擎事件监听器接口。
 */
@FunctionalInterface
public interface EngineEventListener {

    /**
     * 处理引擎事件。
     *
     * @param event 引擎事件
     */
    void onEvent(EngineEvent event);
}

package io.leavesfly.jharness2.engine.heartbeat;

/**
 * 心跳事件监听器。当心跳触发时被回调。
 */
@FunctionalInterface
public interface HeartbeatListener {

    /**
     * 心跳触发回调。
     *
     * @param event 心跳事件详情
     */
    void onHeartbeat(HeartbeatEvent event);
}

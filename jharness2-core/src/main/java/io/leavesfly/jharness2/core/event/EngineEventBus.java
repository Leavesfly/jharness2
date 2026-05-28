package io.leavesfly.jharness2.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 引擎事件总线 —— 发布/订阅引擎生命周期事件。
 * <p>
 * 支持按事件类型订阅和全局订阅两种模式。
 * 事件分发默认异步执行，避免阻塞核心路径。
 */
public class EngineEventBus {

    private static final Logger logger = LoggerFactory.getLogger(EngineEventBus.class);

    private final List<EngineEventListener> globalListeners = new CopyOnWriteArrayList<>();
    private final Map<String, List<EngineEventListener>> typedListeners = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public EngineEventBus() {
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "jharness2-event-bus");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 注册全局监听器（接收所有事件）。
     */
    public void subscribe(EngineEventListener listener) {
        globalListeners.add(listener);
    }

    /**
     * 注册按事件类型过滤的监听器。
     *
     * @param eventType 事件类型（如 "engine.created"、"engine.evicted"）
     * @param listener  监听器
     */
    public void subscribe(String eventType, EngineEventListener listener) {
        typedListeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 移除监听器。
     */
    public void unsubscribe(EngineEventListener listener) {
        globalListeners.remove(listener);
        typedListeners.values().forEach(list -> list.remove(listener));
    }

    /**
     * 发布事件（异步分发）。
     */
    public void publish(EngineEvent event) {
        executor.submit(() -> dispatch(event));
    }

    /**
     * 发布事件（同步分发，用于测试或关键路径）。
     */
    public void publishSync(EngineEvent event) {
        dispatch(event);
    }

    private void dispatch(EngineEvent event) {
        // 分发给全局监听器
        for (EngineEventListener listener : globalListeners) {
            invokeListener(listener, event);
        }

        // 分发给按类型注册的监听器
        List<EngineEventListener> typed = typedListeners.get(event.getEventType());
        if (typed != null) {
            for (EngineEventListener listener : typed) {
                invokeListener(listener, event);
            }
        }
    }

    private void invokeListener(EngineEventListener listener, EngineEvent event) {
        try {
            listener.onEvent(event);
        } catch (Exception e) {
            logger.warn("Event listener failed for event type={}: {}",
                    event.getEventType(), e.getMessage());
        }
    }

    /**
     * 关闭事件总线。
     */
    public void shutdown() {
        executor.shutdown();
    }
}

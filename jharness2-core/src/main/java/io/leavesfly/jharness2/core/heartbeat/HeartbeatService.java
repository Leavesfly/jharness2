package io.leavesfly.jharness2.core.heartbeat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 心跳服务 - 提供类似 OpenClaw 的周期性心跳能力。
 * <p>
 * 按照配置的间隔定期触发心跳事件，通知所有已注册的监听器。
 * 可用于健康检查、连接保活、周期性状态上报等场景。
 */
public class HeartbeatService {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);

    private final HeartbeatConfig config;
    private final List<HeartbeatListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> heartbeatFuture;

    public HeartbeatService(HeartbeatConfig config) {
        this.config = config;
    }

    public HeartbeatService() {
        this(new HeartbeatConfig());
    }

    /** 注册心跳监听器 */
    public void addListener(HeartbeatListener listener) {
        listeners.add(listener);
    }

    /** 移除心跳监听器 */
    public void removeListener(HeartbeatListener listener) {
        listeners.remove(listener);
    }

    /** 启动心跳服务 */
    public synchronized void start() {
        if (!config.isEnabled()) {
            logger.info("Heartbeat service is disabled, skipping start");
            return;
        }
        if (running.get()) {
            logger.warn("Heartbeat service is already running");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "jharness2-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        heartbeatFuture = scheduler.scheduleAtFixedRate(
                this::beat,
                config.getInitialDelayMs(),
                config.getIntervalMs(),
                TimeUnit.MILLISECONDS
        );

        running.set(true);
        logger.info("Heartbeat service started: interval={}ms, initialDelay={}ms",
                config.getIntervalMs(), config.getInitialDelayMs());
    }

    /** 停止心跳服务 */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        logger.info("Heartbeat service stopped after {} beats", sequenceCounter.get());
    }

    /** 手动触发一次心跳（不影响定时周期） */
    public void beatNow() {
        beat();
    }

    private void beat() {
        long seq = sequenceCounter.incrementAndGet();
        HeartbeatEvent event = new HeartbeatEvent(Instant.now(), seq, config.getIntervalMs());

        for (HeartbeatListener listener : listeners) {
            try {
                listener.onHeartbeat(event);
            } catch (Exception e) {
                logger.warn("Heartbeat listener failed at seq={}: {}", seq, e.getMessage());
            }
        }
    }

    public boolean isRunning() { return running.get(); }

    public long getSequenceCount() { return sequenceCounter.get(); }

    public HeartbeatConfig getConfig() { return config; }

    public int getListenerCount() { return listeners.size(); }
}

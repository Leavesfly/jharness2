package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.engine.QueryEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 引擎实例封装 —— 管理 QueryEngine 的生命周期、访问跟踪和优雅关闭。
 */
public class EngineInstance {

    private static final Logger logger = LoggerFactory.getLogger(EngineInstance.class);
    private static final Duration DEFAULT_GRACEFUL_TIMEOUT = Duration.ofSeconds(30);

    private final QueryEngine engine;
    private final UserContext userContext;
    private final Instant createdAt;
    private final AtomicReference<Instant> lastAccessedAt;
    private final AtomicReference<EngineLifecycleState> state;
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public EngineInstance(QueryEngine engine, UserContext userContext) {
        this.engine = engine;
        this.userContext = userContext;
        this.createdAt = Instant.now();
        this.lastAccessedAt = new AtomicReference<>(this.createdAt);
        this.state = new AtomicReference<>(EngineLifecycleState.RUNNING);
    }

    /**
     * 获取引擎并记录访问。调用前应检查状态。
     *
     * @throws IllegalStateException 如果引擎不在 RUNNING 状态
     */
    public QueryEngine getEngine() {
        if (state.get() != EngineLifecycleState.RUNNING) {
            throw new IllegalStateException(
                    "Engine is not available, current state: " + state.get());
        }
        lastAccessedAt.set(Instant.now());
        return engine;
    }

    /**
     * 标记一个请求开始（用于优雅关闭时等待活跃请求完成）。
     */
    public void acquireRequest() {
        activeRequests.incrementAndGet();
    }

    /**
     * 标记一个请求结束。
     */
    public void releaseRequest() {
        activeRequests.decrementAndGet();
    }

    /**
     * 当前活跃请求数。
     */
    public int getActiveRequestCount() {
        return activeRequests.get();
    }

    /**
     * 优雅关闭引擎：
     * 1. 切换状态为 SHUTTING_DOWN（拒绝新请求）
     * 2. 等待当前活跃请求完成（最多等待 gracefulTimeout）
     * 3. 触发 session 持久化
     * 4. 关闭引擎
     * 5. 切换状态为 CLOSED
     */
    public CompletableFuture<Void> gracefulClose() {
        return gracefulClose(DEFAULT_GRACEFUL_TIMEOUT);
    }

    public CompletableFuture<Void> gracefulClose(Duration gracefulTimeout) {
        if (!state.compareAndSet(EngineLifecycleState.RUNNING, EngineLifecycleState.SHUTTING_DOWN)) {
            // 已经在关闭中或已关闭
            return CompletableFuture.completedFuture(null);
        }

        String key = userContext.getCacheKey();
        logger.info("Graceful shutdown initiated for engine: {}", key);

        return CompletableFuture.runAsync(() -> {
            try {
                // 等待活跃请求完成
                long deadline = System.currentTimeMillis() + gracefulTimeout.toMillis();
                while (activeRequests.get() > 0 && System.currentTimeMillis() < deadline) {
                    TimeUnit.MILLISECONDS.sleep(100);
                }

                if (activeRequests.get() > 0) {
                    logger.warn("Graceful timeout reached with {} active requests for engine: {}",
                            activeRequests.get(), key);
                    engine.cancel();
                }

                // 触发 session 持久化（引擎内部的 SessionPersister 会保存状态）
                persistBeforeClose();

                // 最终关闭引擎
                engine.close();
                state.set(EngineLifecycleState.CLOSED);
                logger.info("Engine gracefully closed: {}", key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                forceClose();
            } catch (Exception e) {
                logger.error("Error during graceful shutdown for engine: {}", key, e);
                forceClose();
            }
        });
    }

    /**
     * 立即强制关闭（向后兼容）。
     */
    public void close() {
        EngineLifecycleState previous = state.getAndSet(EngineLifecycleState.CLOSED);
        if (previous == EngineLifecycleState.CLOSED) {
            return;
        }
        try {
            persistBeforeClose();
        } catch (Exception e) {
            logger.debug("Persist before close failed (ignored): {}", e.getMessage());
        }
        engine.close();
    }

    public boolean isRunning() {
        return state.get() == EngineLifecycleState.RUNNING;
    }

    public boolean isClosed() {
        return state.get() == EngineLifecycleState.CLOSED;
    }

    public EngineLifecycleState getLifecycleState() {
        return state.get();
    }

    public UserContext getUserContext() { return userContext; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt.get(); }

    private void forceClose() {
        state.set(EngineLifecycleState.CLOSED);
        try {
            engine.close();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void persistBeforeClose() {
        if (engine.getSessionPersister() != null && !engine.getMessages().isEmpty()) {
            engine.getSessionPersister().persist(engine.getMessages());
        }
    }
}

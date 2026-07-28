package io.leavesfly.jharness2.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.leavesfly.jharness2.core.checkpoint.SessionCheckpointService;
import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单机引擎注册表 —— 使用 Caffeine 本地缓存管理引擎实例（默认实现）。
 * <p>
 * 并发保证：
 * <ul>
 *   <li>同 key 并发 getOrCreate 通过 Caffeine 原子加载只创建一个引擎</li>
 *   <li>每用户配额采用"先占位后回滚"策略，并发下不可突破</li>
 *   <li>驱逐走优雅关闭（等待活跃请求完成再持久化、关闭）</li>
 *   <li>驱逐尚未完成时的新请求会等待关闭完成，避免同一 session 出现两个引擎并发写消息历史</li>
 *   <li>缓存未命中时优先从持久化存储恢复会话历史，避免用户"失忆"</li>
 * </ul>
 */
public class LocalEngineRegistry implements UserEngineRegistry {

    private static final Logger logger = LoggerFactory.getLogger(LocalEngineRegistry.class);
    /** 等待同 session 前一个引擎关闭完成的最长时间 */
    private static final Duration CLOSING_WAIT_TIMEOUT = Duration.ofSeconds(35);

    private final EngineFactory engineFactory;
    private final EngineConfig engineConfig;
    private final SessionPersistenceService sessionPersistence;
    /** 可选：DB 快照不可用时的 checkpoint 兑底恢复源 */
    private final SessionCheckpointService checkpointService;

    private Cache<String, EngineInstance> engineCache;
    private final ConcurrentMap<String, AtomicInteger> userEngineCount = new ConcurrentHashMap<>();
    /** 正在优雅关闭中的引擎：cacheKey -> 关闭完成 Future，用于阻止同 session 提前重建 */
    private final ConcurrentMap<String, CompletableFuture<Void>> closingEngines = new ConcurrentHashMap<>();

    public LocalEngineRegistry(EngineFactory engineFactory, EngineConfig engineConfig) {
        this(engineFactory, engineConfig, null, null);
    }

    public LocalEngineRegistry(EngineFactory engineFactory, EngineConfig engineConfig,
                               SessionPersistenceService sessionPersistence) {
        this(engineFactory, engineConfig, sessionPersistence, null);
    }

    public LocalEngineRegistry(EngineFactory engineFactory, EngineConfig engineConfig,
                               SessionPersistenceService sessionPersistence,
                               SessionCheckpointService checkpointService) {
        this.engineFactory = engineFactory;
        this.engineConfig = engineConfig;
        this.sessionPersistence = sessionPersistence;
        this.checkpointService = checkpointService;
    }

    @PostConstruct
    public void init() {
        this.engineCache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(engineConfig.getEngineIdleTimeoutMinutes()))
                .maximumSize(engineConfig.getMaxTotalEngines())
                .removalListener((String key, EngineInstance instance, RemovalCause cause) -> {
                    if (instance == null) {
                        return;
                    }
                    if (cause == RemovalCause.REPLACED) {
                        // 同 key 被新实例覆盖：旧实例的资源由覆盖方负责，不能在此递减配额
                        return;
                    }
                    logger.info("Evicting engine: key={}, cause={}", key, cause);
                    // 优雅关闭：异步等待活跃请求完成后再持久化并关闭，
                    // 避免在缓存维护线程上同步强杀正在服务的引擎
                    trackClosing(key, instance);
                    String userId = instance.getUserContext().getUserId();
                    releaseUserSlot(userId);
                })
                .build();
    }

    @Override
    public EngineInstance getOrCreate(UserContext context) {
        String cacheKey = context.getCacheKey();
        EngineInstance existing = engineCache.getIfPresent(cacheKey);
        if (existing != null && !existing.isRunning()) {
            // 已关闭/关闭中的实例不可复用，先失效再重建
            engineCache.invalidate(cacheKey);
        }
        // 若该 session 的上一个引擎仍在优雅关闭中，先等它落库完成再重建，
        // 否则新旧两个引擎会同时持有同一 session 的消息历史并互相覆盖
        awaitPreviousClose(cacheKey);
        // Caffeine 原子加载：同 key 并发只执行一次创建，避免重复创建后互相覆盖
        return engineCache.get(cacheKey, key -> createOrRestore(context));
    }

    /**
     * 登记一个正在关闭的引擎，并在关闭完成后清理登记。
     */
    private void trackClosing(String cacheKey, EngineInstance instance) {
        CompletableFuture<Void> closing = instance.gracefulClose();
        closingEngines.put(cacheKey, closing);
        closing.whenComplete((v, e) -> closingEngines.remove(cacheKey, closing));
    }

    /**
     * 等待同 session 的前一个引擎关闭完成。超时则放弃等待并继续重建
     * （优雅关闭内部自带超时兜底，不会无限期悬挂）。
     */
    private void awaitPreviousClose(String cacheKey) {
        CompletableFuture<Void> closing = closingEngines.get(cacheKey);
        if (closing == null || closing.isDone()) {
            return;
        }
        try {
            closing.get(CLOSING_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warn("Timed out waiting for previous engine to close: key={}", cacheKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.debug("Previous engine close failed (ignored): key={}, error={}", cacheKey, e.getMessage());
        }
    }

    private EngineInstance createOrRestore(UserContext context) {
        // 先占位后回滚：并发下配额不可突破
        acquireUserSlot(context.getUserId());
        try {
            EngineInstance restored = restoreFromStorage(context);
            return restored != null ? restored : engineFactory.create(context);
        } catch (RuntimeException e) {
            releaseUserSlot(context.getUserId());
            throw e;
        }
    }

    /**
     * 占用一个用户引擎槽位，超限时抛 {@link EngineLimitException}。
     * <p>
     * 占位成功后会校验计数器仍在 map 中：否则它已被 {@link #releaseUserSlot} 清理，
     * 继续持有将变成“游离计数”而绕过每用户上限。
     */
    private void acquireUserSlot(String userId) {
        int max = engineConfig.getMaxEnginesPerUser();
        while (true) {
            AtomicInteger count = userEngineCount.computeIfAbsent(userId, k -> new AtomicInteger(0));
            if (count.incrementAndGet() > max) {
                count.decrementAndGet();
                throw new EngineLimitException(
                        "User " + userId + " has reached max engine limit: " + max);
            }
            if (userEngineCount.get(userId) == count) {
                return;
            }
            count.decrementAndGet();
        }
    }

    /**
     * 归还用户配额槽位，计数归零时移除条目，避免用户维度的 map 无界增长。
     */
    private void releaseUserSlot(String userId) {
        userEngineCount.compute(userId, (k, counter) -> {
            if (counter == null) {
                return null;
            }
            return counter.decrementAndGet() > 0 ? counter : null;
        });
    }

    /**
     * 尝试从持久化存储恢复会话历史（引擎曾被驱逐或服务重启的场景）。
     * DB 快照加载失败或为空时，回退到 checkpoint 快照（内存/外置 store）兑底。
     */
    private EngineInstance restoreFromStorage(UserContext context) {
        if (sessionPersistence != null) {
            try {
                EngineInstance restored = sessionPersistence.loadSession(context.getUserId(), context.getSessionId())
                        .map(snapshot -> engineFactory.restore(context, snapshot.messages(),
                                snapshot.inputTokens(), snapshot.outputTokens()))
                        .orElse(null);
                if (restored != null) {
                    return restored;
                }
            } catch (Exception e) {
                logger.warn("Session restore failed, trying checkpoint fallback: user={}, session={}, error={}",
                        context.getUserId(), context.getSessionId(), e.getMessage());
            }
        }
        return restoreFromCheckpoint(context);
    }

    /**
     * checkpoint 兑底恢复：主持久化链路不可用时的第二恢复源。
     */
    private EngineInstance restoreFromCheckpoint(UserContext context) {
        if (checkpointService == null) {
            return null;
        }
        try {
            return checkpointService.loadLatest(context.getUserId(), context.getSessionId())
                    .map(data -> {
                        logger.info("Restoring engine from checkpoint: user={}, session={}, messages={}",
                                context.getUserId(), context.getSessionId(), data.getMessages().size());
                        return engineFactory.restore(context, data.getMessages(),
                                data.getInputTokens(), data.getOutputTokens());
                    })
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Checkpoint restore failed, falling back to fresh engine: user={}, session={}, error={}",
                    context.getUserId(), context.getSessionId(), e.getMessage());
            return null;
        }
    }

    @Override
    public EngineInstance get(String userId, String sessionId) {
        return engineCache.getIfPresent(userId + ":" + sessionId);
    }

    @Override
    public void evict(String userId, String sessionId) {
        engineCache.invalidate(userId + ":" + sessionId);
    }

    @Override
    public long activeEngineCount() {
        return engineCache.estimatedSize();
    }

    @Override
    public int userEngineCount(String userId) {
        AtomicInteger count = userEngineCount.get(userId);
        return count != null ? count.get() : 0;
    }

    @Override
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down LocalEngineRegistry, closing {} engines", engineCache.estimatedSize());
        // 应用停机：同步关闭（内部会先持久化会话），保证 JVM 退出前落库
        engineCache.asMap().values().forEach(EngineInstance::close);
        engineCache.invalidateAll();
    }
}

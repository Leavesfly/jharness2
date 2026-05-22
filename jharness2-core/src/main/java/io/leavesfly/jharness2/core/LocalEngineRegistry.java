package io.leavesfly.jharness2.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单机引擎注册表 —— 使用 Caffeine 本地缓存管理引擎实例（默认实现）。
 */
public class LocalEngineRegistry implements UserEngineRegistry {

    private static final Logger logger = LoggerFactory.getLogger(LocalEngineRegistry.class);

    private final EngineFactory engineFactory;
    private final EngineConfig engineConfig;

    private Cache<String, EngineInstance> engineCache;
    private final ConcurrentMap<String, AtomicInteger> userEngineCount = new ConcurrentHashMap<>();

    public LocalEngineRegistry(EngineFactory engineFactory, EngineConfig engineConfig) {
        this.engineFactory = engineFactory;
        this.engineConfig = engineConfig;
    }

    @PostConstruct
    public void init() {
        this.engineCache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(engineConfig.getEngineIdleTimeoutMinutes()))
                .maximumSize(engineConfig.getMaxTotalEngines())
                .removalListener((String key, EngineInstance instance, RemovalCause cause) -> {
                    if (instance != null) {
                        logger.info("Evicting engine: key={}, cause={}", key, cause);
                        instance.close();
                        String userId = instance.getUserContext().getUserId();
                        userEngineCount.computeIfPresent(userId, (k, v) -> {
                            v.decrementAndGet();
                            return v;
                        });
                    }
                })
                .build();
    }

    @Override
    public EngineInstance getOrCreate(UserContext context) {
        String cacheKey = context.getCacheKey();
        EngineInstance existing = engineCache.getIfPresent(cacheKey);
        if (existing != null) {
            return existing;
        }

        AtomicInteger count = userEngineCount.computeIfAbsent(
                context.getUserId(), k -> new AtomicInteger(0));
        if (count.get() >= engineConfig.getMaxEnginesPerUser()) {
            throw new EngineLimitExceededException(
                    "User " + context.getUserId() + " has reached max engine limit: "
                            + engineConfig.getMaxEnginesPerUser());
        }

        EngineInstance instance = engineFactory.create(context);
        engineCache.put(cacheKey, instance);
        count.incrementAndGet();
        return instance;
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
        engineCache.asMap().values().forEach(EngineInstance::close);
        engineCache.invalidateAll();
    }
}

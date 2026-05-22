package io.leavesfly.jharness2.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UserEngineRegistry {

    private static final Logger logger = LoggerFactory.getLogger(UserEngineRegistry.class);

    private final EngineFactory engineFactory;
    private final EngineConfig engineConfig;

    private Cache<String, EngineInstance> engineCache;
    private final ConcurrentMap<String, AtomicInteger> userEngineCount = new ConcurrentHashMap<>();

    public UserEngineRegistry(EngineFactory engineFactory, EngineConfig engineConfig) {
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

    public EngineInstance getOrCreate(UserContext context) {
        String cacheKey = context.getCacheKey();
        EngineInstance existing = engineCache.getIfPresent(cacheKey);
        if (existing != null) {
            return existing;
        }

        // 检查用户 engine 数量限制
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

    public EngineInstance get(String userId, String sessionId) {
        return engineCache.getIfPresent(userId + ":" + sessionId);
    }

    public void evict(String userId, String sessionId) {
        engineCache.invalidate(userId + ":" + sessionId);
    }

    public long activeEngineCount() {
        return engineCache.estimatedSize();
    }

    public int userEngineCount(String userId) {
        AtomicInteger count = userEngineCount.get(userId);
        return count != null ? count.get() : 0;
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down UserEngineRegistry, closing {} engines", engineCache.estimatedSize());
        engineCache.asMap().values().forEach(EngineInstance::close);
        engineCache.invalidateAll();
    }
}

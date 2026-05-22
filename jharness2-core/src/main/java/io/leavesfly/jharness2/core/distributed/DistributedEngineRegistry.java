package io.leavesfly.jharness2.core.distributed;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.leavesfly.jharness2.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * 分布式引擎注册表 —— Redis 状态外置 + 本地 Caffeine 一级缓存。
 * <p>
 * 工作流程：
 * 1. 本地缓存命中 → 直接返回（热路径零延迟）
 * 2. 本地未命中 → 从 Redis 加载状态 → 获取所有权 → 恢复引擎
 * 3. Redis 也无 → 全新创建，状态写入 Redis
 * <p>
 * 定时心跳将本地引擎状态同步到 Redis，并续租所有权。
 */
public class DistributedEngineRegistry implements UserEngineRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DistributedEngineRegistry.class);

    private final EngineFactory engineFactory;
    private final EngineConfig engineConfig;
    private final EngineStateStore stateStore;
    private final String nodeId;

    private Cache<String, EngineInstance> localCache;

    public DistributedEngineRegistry(EngineFactory engineFactory,
                                     EngineConfig engineConfig,
                                     EngineStateStore stateStore) {
        this.engineFactory = engineFactory;
        this.engineConfig = engineConfig;
        this.stateStore = stateStore;

        String configuredNodeId = engineConfig.getDistributed().getNodeId();
        this.nodeId = (configuredNodeId != null && !configuredNodeId.isBlank())
                ? configuredNodeId
                : java.util.UUID.randomUUID().toString();

        logger.info("DistributedEngineRegistry initialized with nodeId={}", this.nodeId);
    }

    @PostConstruct
    public void init() {
        this.localCache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(engineConfig.getEngineIdleTimeoutMinutes()))
                .maximumSize(engineConfig.getMaxTotalEngines())
                .removalListener((String key, EngineInstance instance, RemovalCause cause) -> {
                    if (instance != null && cause != RemovalCause.EXPLICIT) {
                        // 仅处理非手动驱逐（超时/容量淘汰），手动 evict 已在调用方处理
                        logger.info("Evicting engine from local cache: key={}, cause={}", key, cause);
                        instance.close();
                        onEngineRemoved(key, instance);
                    }
                })
                .build();
    }

    @Override
    public EngineInstance getOrCreate(UserContext context) {
        String cacheKey = context.getCacheKey();

        // 1. 本地缓存命中
        EngineInstance local = localCache.getIfPresent(cacheKey);
        if (local != null) {
            Duration ttl = Duration.ofMinutes(engineConfig.getDistributed().getStateTtlMinutes());
            stateStore.touch(cacheKey, ttl);
            return local;
        }

        // 2. 尝试从 Redis 恢复
        Optional<EngineState> remoteState = stateStore.load(cacheKey);
        if (remoteState.isPresent()) {
            EngineState state = remoteState.get();
            Duration lease = Duration.ofSeconds(engineConfig.getDistributed().getOwnershipLeaseSeconds());

            if (!stateStore.tryAcquireOwnership(cacheKey, nodeId, lease)) {
                logger.warn("Engine owned by another node: key={}, owner={}",
                        cacheKey, state.getOwnerNodeId());
                throw new EngineOwnedByOtherNodeException(
                        "Engine " + cacheKey + " is active on node " + state.getOwnerNodeId());
            }

            // 恢复引擎
            UserContext restoreContext = buildContextFromState(context, state);
            EngineInstance restored = engineFactory.restore(
                    restoreContext, state.getMessages(),
                    state.getInputTokens(), state.getOutputTokens());
            localCache.put(cacheKey, restored);
            logger.info("Restored engine from Redis: key={}, messages={}", cacheKey, state.getMessages().size());
            return restored;
        }

        // 3. 全新创建（检查分布式引擎计数）
        int userCount = stateStore.countByUser(context.getUserId());
        if (userCount >= engineConfig.getMaxEnginesPerUser()) {
            throw new EngineLimitExceededException(
                    "User " + context.getUserId() + " has reached max engine limit (distributed): "
                            + engineConfig.getMaxEnginesPerUser());
        }

        EngineInstance instance = engineFactory.create(context);
        localCache.put(cacheKey, instance);

        // 写入 Redis 状态 + 获取所有权 + 计数
        Duration lease = Duration.ofSeconds(engineConfig.getDistributed().getOwnershipLeaseSeconds());
        stateStore.tryAcquireOwnership(cacheKey, nodeId, lease);
        stateStore.incrementUserCount(context.getUserId());
        syncStateToRedis(cacheKey, instance);

        return instance;
    }

    @Override
    public EngineInstance get(String userId, String sessionId) {
        return localCache.getIfPresent(userId + ":" + sessionId);
    }

    @Override
    public void evict(String userId, String sessionId) {
        String cacheKey = userId + ":" + sessionId;
        EngineInstance instance = localCache.getIfPresent(cacheKey);
        localCache.invalidate(cacheKey);
        // 主动清理分布式状态（不依赖 removalListener 的异步回调）
        if (instance != null) {
            instance.close();
            stateStore.releaseOwnership(cacheKey, nodeId);
            stateStore.decrementUserCount(userId);
            stateStore.remove(cacheKey);
        }
    }

    @Override
    public long activeEngineCount() {
        return localCache.estimatedSize();
    }

    @Override
    public int userEngineCount(String userId) {
        return stateStore.countByUser(userId);
    }

    /**
     * 定期同步本地所有引擎状态到 Redis（由调度器调用）。
     */
    public void syncAllStates() {
        Duration lease = Duration.ofSeconds(engineConfig.getDistributed().getOwnershipLeaseSeconds());
        localCache.asMap().forEach((key, instance) -> {
            try {
                syncStateToRedis(key, instance);
                stateStore.tryAcquireOwnership(key, nodeId, lease);
            } catch (Exception e) {
                logger.warn("Failed to sync engine state to Redis: key={}", key, e);
            }
        });
    }

    @Override
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down DistributedEngineRegistry, releasing {} engines", localCache.estimatedSize());
        localCache.asMap().forEach((key, instance) -> {
            try {
                syncStateToRedis(key, instance);
                stateStore.releaseOwnership(key, nodeId);
            } catch (Exception e) {
                logger.warn("Failed to release engine on shutdown: key={}", key, e);
            }
        });
        localCache.asMap().values().forEach(EngineInstance::close);
        localCache.invalidateAll();
    }

    private void onEngineRemoved(String cacheKey, EngineInstance instance) {
        String userId = instance.getUserContext().getUserId();
        stateStore.releaseOwnership(cacheKey, nodeId);
        stateStore.decrementUserCount(userId);
        stateStore.remove(cacheKey);
    }

    private void syncStateToRedis(String cacheKey, EngineInstance instance) {
        EngineState state = EngineState.capture(instance, nodeId);
        Duration ttl = Duration.ofMinutes(engineConfig.getDistributed().getStateTtlMinutes());
        stateStore.save(cacheKey, state, ttl);
    }

    private UserContext buildContextFromState(UserContext requestContext, EngineState state) {
        // 优先使用请求中的信息，缺失则从 state 补全
        Path workspace = requestContext.getWorkspace() != null
                ? requestContext.getWorkspace()
                : (state.getWorkspacePath() != null ? Path.of(state.getWorkspacePath()) : null);
        String model = requestContext.getModel() != null ? requestContext.getModel() : state.getModel();
        String apiKey = requestContext.getApiKey() != null ? requestContext.getApiKey() : state.getApiKey();
        String baseUrl = requestContext.getBaseUrl() != null ? requestContext.getBaseUrl() : state.getBaseUrl();

        return new UserContext(
                requestContext.getUserId(),
                requestContext.getSessionId(),
                workspace, model, apiKey, baseUrl);
    }

    public String getNodeId() {
        return nodeId;
    }
}

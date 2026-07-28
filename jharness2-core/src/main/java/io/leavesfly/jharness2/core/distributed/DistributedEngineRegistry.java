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
                    if (instance == null) {
                        return;
                    }
                    if (cause == RemovalCause.EXPLICIT) {
                        // 手动 evict 已在调用方处理（关闭引擎、释放所有权、清理计数）
                        return;
                    }
                    if (cause == RemovalCause.REPLACED) {
                        // 同 key 被新实例覆盖：此时 Redis 上的状态/所有权/计数属于新实例，
                        // 在此清理会把刚创建的引擎变成“无主”，只能关闭旧实例本身
                        logger.info("Engine instance replaced in local cache: key={}", key);
                        instance.gracefulClose();
                        return;
                    }
                    logger.info("Evicting engine from local cache: key={}, cause={}", key, cause);
                    // 优雅关闭：等活跃请求完成后再持久化关闭，避免硬杀正在服务的会话
                    instance.gracefulClose().whenComplete((v, e) -> onEngineRemoved(key, instance));
                })
                .build();
    }
    
    @Override
    public EngineInstance getOrCreate(UserContext context) {
        String cacheKey = context.getCacheKey();
        Duration lease = Duration.ofSeconds(engineConfig.getDistributed().getOwnershipLeaseSeconds());
    
        // 1. 本地缓存命中：同时续租，避免长会话期间租约到期被其他节点接管
        EngineInstance local = localCache.getIfPresent(cacheKey);
        if (local != null && local.isRunning()) {
            stateStore.touch(cacheKey, Duration.ofMinutes(engineConfig.getDistributed().getStateTtlMinutes()));
            stateStore.tryAcquireOwnership(cacheKey, nodeId, lease);
            return local;
        }
        if (local != null) {
            // 已关闭/关闭中的实例不可复用
            localCache.invalidate(cacheKey);
        }
    
        // 2. 尝试从 Redis 恢复（先拿到所有权再恢复，避免两个节点同时持有同一 session）
        Optional<EngineState> remoteState = stateStore.load(cacheKey);
        if (remoteState.isPresent()) {
            EngineState state = remoteState.get();
            if (!stateStore.tryAcquireOwnership(cacheKey, nodeId, lease)) {
                logger.warn("Engine owned by another node: key={}, owner={}",
                        cacheKey, state.getOwnerNodeId());
                throw new EngineOwnedByOtherNodeException(
                        "Engine " + cacheKey + " is active on node " + state.getOwnerNodeId(),
                        state.getOwnerNodeId());
            }
    
            UserContext restoreContext = buildContextFromState(context, state);
            EngineInstance restored = engineFactory.restore(
                    restoreContext, state.getMessages(),
                    state.getInputTokens(), state.getOutputTokens());
            localCache.put(cacheKey, restored);
            logger.info("Restored engine from Redis: key={}, messages={}", cacheKey,
                    state.getMessages() != null ? state.getMessages().size() : 0);
            return restored;
        }
    
        // 3. 全新创建：原子占位分布式计数，避免多节点同时突破每用户上限
        if (!stateStore.tryIncrementUserCount(context.getUserId(), engineConfig.getMaxEnginesPerUser())) {
            throw new EngineLimitException(
                    "User " + context.getUserId() + " has reached max engine limit (distributed): "
                            + engineConfig.getMaxEnginesPerUser());
        }
    
        EngineInstance instance;
        try {
            instance = engineFactory.create(context);
            if (!stateStore.tryAcquireOwnership(cacheKey, nodeId, lease)) {
                instance.close();
                // 创建期竞争：重读状态尽力获取新属主，供上层转发
                String claimedBy = stateStore.load(cacheKey)
                        .map(EngineState::getOwnerNodeId).orElse(null);
                throw new EngineOwnedByOtherNodeException(
                        "Engine " + cacheKey + " was claimed by another node", claimedBy);
            }
            syncStateToRedis(cacheKey, instance);
        } catch (RuntimeException e) {
            // 任一步失败都需归还计数，否则计数只增不减会永久堵死该用户
            stateStore.decrementUserCount(context.getUserId());
            throw e;
        }
        localCache.put(cacheKey, instance);
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
     * <p>
     * 先续租再写入：若续租失败（说明已被其他节点接管），则立即停止写入并丢弃本地实例，
     * 否则旧节点会持续用陈旧快照覆盖新节点的会话历史，造成丢消息。
     */
    public void syncAllStates() {
        Duration lease = Duration.ofSeconds(engineConfig.getDistributed().getOwnershipLeaseSeconds());
        localCache.asMap().forEach((key, instance) -> {
            try {
                if (!stateStore.tryAcquireOwnership(key, nodeId, lease)) {
                    logger.warn("Lost ownership of engine, dropping local instance: key={}", key);
                    localCache.invalidate(key);
                    instance.gracefulClose();
                    return;
                }
                syncStateToRedis(key, instance);
            } catch (Exception e) {
                logger.warn("Failed to sync engine state to Redis: key={}", key, e);
            }
        });
    }

    /**
     * 释放本节点所有引擎的所有权并关闭本地实例，供节点排空（drain）使用。
     * <p>
     * 不删除 Redis 中的会话状态也不递减计数：引擎要被其他节点接管，而不是销毁。
     *
     * @return 成功交出的 cacheKey 列表
     */
    public java.util.List<String> releaseAllForMigration() {
        java.util.List<String> released = new java.util.ArrayList<>();
        localCache.asMap().forEach((key, instance) -> {
            try {
                // 先等活跃请求停下并落库，再把最新状态交给 Redis，最后让渡所有权
                instance.gracefulClose().join();
                syncStateToRedis(key, instance);
                stateStore.releaseOwnership(key, nodeId);
                released.add(key);
            } catch (Exception e) {
                logger.warn("Failed to release engine for migration: key={}", key, e);
            }
        });
        localCache.invalidateAll();
        return released;
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

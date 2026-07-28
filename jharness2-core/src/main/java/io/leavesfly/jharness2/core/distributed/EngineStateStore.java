package io.leavesfly.jharness2.core.distributed;

import java.time.Duration;
import java.util.Optional;

/**
 * 引擎状态的分布式存储接口。
 */
public interface EngineStateStore {

    /**
     * 保存/更新引擎状态。
     */
    void save(String cacheKey, EngineState state, Duration ttl);

    /**
     * 加载引擎状态。
     */
    Optional<EngineState> load(String cacheKey);

    /**
     * 删除引擎状态。
     */
    void remove(String cacheKey);

    /**
     * 刷新存活时间。
     */
    void touch(String cacheKey, Duration ttl);

    /**
     * 获取用户的引擎数量（分布式计数）。
     */
    int countByUser(String userId);

    /**
     * 增加用户引擎计数。
     */
    void incrementUserCount(String userId);

    /**
     * 减少用户引擎计数。
     */
    void decrementUserCount(String userId);

    /**
     * 原子地“校验上限并递增”用户引擎计数。
     * <p>
     * 分开的 {@code countByUser} + {@code incrementUserCount} 在多节点并发下
     * 会让同一用户突破引擎数上限，因此提供原子版本。
     * 默认实现退化为非原子版，以兼容不支持原子操作的存储后端。
     *
     * @return 占位成功返回 true；已达上限返回 false（不改变计数）
     */
    default boolean tryIncrementUserCount(String userId, int maxCount) {
        if (countByUser(userId) >= maxCount) {
            return false;
        }
        incrementUserCount(userId);
        return true;
    }

    /**
     * 尝试获取引擎所有权（分布式锁/租约）。
     */
    boolean tryAcquireOwnership(String cacheKey, String nodeId, Duration lease);

    /**
     * 释放引擎所有权。
     */
    void releaseOwnership(String cacheKey, String nodeId);
}

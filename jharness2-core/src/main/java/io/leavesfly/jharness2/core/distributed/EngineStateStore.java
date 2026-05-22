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
     * 尝试获取引擎所有权（分布式锁/租约）。
     */
    boolean tryAcquireOwnership(String cacheKey, String nodeId, Duration lease);

    /**
     * 释放引擎所有权。
     */
    void releaseOwnership(String cacheKey, String nodeId);
}

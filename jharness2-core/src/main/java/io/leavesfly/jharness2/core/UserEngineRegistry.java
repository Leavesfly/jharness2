package io.leavesfly.jharness2.core;

/**
 * 引擎注册表接口 —— 管理引擎实例的创建、获取、驱逐和生命周期。
 * <p>
 * 两种实现：
 * - {@code LocalEngineRegistry}：单机 Caffeine 缓存（默认）
 * - {@code DistributedEngineRegistry}：Redis 状态外置 + 本地缓存（需开启分布式配置）
 */
public interface UserEngineRegistry {

    EngineInstance getOrCreate(UserContext context);

    EngineInstance get(String userId, String sessionId);

    void evict(String userId, String sessionId);

    long activeEngineCount();

    int userEngineCount(String userId);

    void shutdown();
}

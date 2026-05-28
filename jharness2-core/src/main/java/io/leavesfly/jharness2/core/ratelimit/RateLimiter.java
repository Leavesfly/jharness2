package io.leavesfly.jharness2.core.ratelimit;

/**
 * 限流器接口 —— 基于用户维度控制请求速率。
 */
public interface RateLimiter {

    /**
     * 尝试获取一个许可。
     *
     * @param userId 用户标识
     * @return 是否允许通过
     */
    boolean tryAcquire(String userId);

    /**
     * 获取用户当前窗口内的剩余许可数。
     *
     * @param userId 用户标识
     * @return 剩余许可数
     */
    int remainingPermits(String userId);

    /**
     * 重置用户的限流状态。
     */
    void reset(String userId);
}

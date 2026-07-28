package io.leavesfly.jharness2.core.quota;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 每用户并发请求限制器 —— 让 {@link QuotaLimit#getMaxConcurrentRequests()} 真正生效。
 * <p>
 * 采用"先占位后回滚"的 CAS 策略，并发下不可突破上限。
 * 与限流器（单位时间请求数）互补：这里控制的是同时在跑的请求数。
 */
public class ConcurrencyLimiter {

    private final ConcurrentMap<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();

    /**
     * 尝试为用户占用一个并发槽位。
     *
     * @return 成功占用返回 true；已达上限返回 false（此时不占用槽位）
     */
    public boolean tryAcquire(String userId, int maxConcurrent) {
        if (maxConcurrent <= 0) {
            return true;
        }
        while (true) {
            AtomicInteger counter = inFlight.computeIfAbsent(userId, k -> new AtomicInteger(0));
            int current = counter.get();
            if (current >= maxConcurrent) {
                return false;
            }
            if (!counter.compareAndSet(current, current + 1)) {
                continue;
            }
            // 占位成功后确认计数器仍在 map 中：否则它已被 release 清理，
            // 继续持有会变成“游离计数”而绕过上限，此时回退重试
            if (inFlight.get(userId) == counter) {
                return true;
            }
            counter.decrementAndGet();
        }
    }

    /**
     * 释放一个并发槽位。计数归零时移除条目，避免用户维度的 map 无界增长。
     */
    public void release(String userId) {
        inFlight.compute(userId, (k, counter) -> {
            if (counter == null) {
                return null;
            }
            return counter.decrementAndGet() > 0 ? counter : null;
        });
    }

    public int inFlightCount(String userId) {
        AtomicInteger counter = inFlight.get(userId);
        return counter != null ? counter.get() : 0;
    }
}

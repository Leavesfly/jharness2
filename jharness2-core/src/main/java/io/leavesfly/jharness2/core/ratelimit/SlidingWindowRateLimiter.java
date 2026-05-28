package io.leavesfly.jharness2.core.ratelimit;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 基于滑动窗口的限流器（本地实现）。
 * <p>
 * 每个用户维护一个时间戳队列，只保留窗口内的请求记录。
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private final int maxRequestsPerWindow;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Deque<Long>> userWindows = new ConcurrentHashMap<>();

    /**
     * @param maxRequestsPerWindow 窗口内最大请求数
     * @param windowMillis         窗口大小（毫秒）
     */
    public SlidingWindowRateLimiter(int maxRequestsPerWindow, long windowMillis) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean tryAcquire(String userId) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> window = userWindows.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());

        // 清除窗口外的旧记录
        evictExpired(window, now);

        if (window.size() >= maxRequestsPerWindow) {
            return false;
        }

        window.addLast(now);
        return true;
    }

    @Override
    public int remainingPermits(String userId) {
        Deque<Long> window = userWindows.get(userId);
        if (window == null) {
            return maxRequestsPerWindow;
        }
        evictExpired(window, Instant.now().toEpochMilli());
        return Math.max(0, maxRequestsPerWindow - window.size());
    }

    @Override
    public void reset(String userId) {
        userWindows.remove(userId);
    }

    private void evictExpired(Deque<Long> window, long now) {
        long cutoff = now - windowMillis;
        while (!window.isEmpty() && window.peekFirst() < cutoff) {
            window.pollFirst();
        }
    }
}

package io.leavesfly.jharness2.core.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowRateLimiterTest {

    @Test
    void shouldAllowRequestsWithinLimit() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, 60_000L);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("user1"), "Request " + i + " should be allowed");
        }
    }

    @Test
    void shouldDenyRequestsOverLimit() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, 60_000L);
        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));
        assertFalse(limiter.tryAcquire("user1"));
    }

    @Test
    void differentUsersShouldHaveIndependentLimits() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, 60_000L);
        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));
        assertFalse(limiter.tryAcquire("user1"));

        // user2 应不受 user1 的影响
        assertTrue(limiter.tryAcquire("user2"));
        assertTrue(limiter.tryAcquire("user2"));
    }

    @Test
    void shouldAllowAfterWindowExpires() throws Exception {
        // 使用 100ms 窗口便于测试
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, 100L);
        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));
        assertFalse(limiter.tryAcquire("user1"));

        // 等待窗口过期
        Thread.sleep(150);
        assertTrue(limiter.tryAcquire("user1"));
    }

    @Test
    void remainingPermitsShouldBeAccurate() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, 60_000L);
        assertEquals(5, limiter.remainingPermits("user1"));
        limiter.tryAcquire("user1");
        assertEquals(4, limiter.remainingPermits("user1"));
        limiter.tryAcquire("user1");
        limiter.tryAcquire("user1");
        assertEquals(2, limiter.remainingPermits("user1"));
    }

    @Test
    void resetShouldClearUserState() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, 60_000L);
        limiter.tryAcquire("user1");
        limiter.tryAcquire("user1");
        assertFalse(limiter.tryAcquire("user1"));

        limiter.reset("user1");
        assertTrue(limiter.tryAcquire("user1"));
        assertEquals(1, limiter.remainingPermits("user1"));
    }
}

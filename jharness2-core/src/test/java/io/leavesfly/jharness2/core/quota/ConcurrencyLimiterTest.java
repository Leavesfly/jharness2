package io.leavesfly.jharness2.core.quota;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyLimiterTest {

    @Test
    void shouldAllowUpToLimit() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter();
        assertTrue(limiter.tryAcquire("u1", 2));
        assertTrue(limiter.tryAcquire("u1", 2));
        assertFalse(limiter.tryAcquire("u1", 2));
        assertEquals(2, limiter.inFlightCount("u1"));
    }

    @Test
    void shouldAllowAgainAfterRelease() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter();
        assertTrue(limiter.tryAcquire("u1", 1));
        assertFalse(limiter.tryAcquire("u1", 1));
        limiter.release("u1");
        assertEquals(0, limiter.inFlightCount("u1"));
        assertTrue(limiter.tryAcquire("u1", 1));
    }

    @Test
    void usersShouldBeIndependent() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter();
        assertTrue(limiter.tryAcquire("u1", 1));
        assertTrue(limiter.tryAcquire("u2", 1));
        assertFalse(limiter.tryAcquire("u1", 1));
    }

    @Test
    void nonPositiveLimitMeansUnlimited() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter();
        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.tryAcquire("u1", 0));
        }
    }

    @Test
    void releaseWithoutAcquireShouldNotGoNegative() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter();
        limiter.release("ghost");
        assertEquals(0, limiter.inFlightCount("ghost"));
        assertTrue(limiter.tryAcquire("ghost", 1));
    }

    /**
     * 并发下不可突破上限：这是限流类组件最容易出现 check-then-act 竞态的地方。
     */
    @Test
    void shouldNotExceedLimitUnderConcurrency() throws Exception {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter();
        int threads = 32;
        int max = 4;
        AtomicInteger granted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (limiter.tryAcquire("hot-user", max)) {
                            granted.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(max, granted.get(), "并发获取的槽位数不应超过上限");
        assertEquals(max, limiter.inFlightCount("hot-user"));
    }
}

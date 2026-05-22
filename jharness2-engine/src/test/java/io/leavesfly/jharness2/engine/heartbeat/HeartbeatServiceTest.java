package io.leavesfly.jharness2.engine.heartbeat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatServiceTest {

    private HeartbeatService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.stop();
        }
    }

    @Test
    void shouldFireHeartbeatAtConfiguredInterval() throws InterruptedException {
        HeartbeatConfig config = new HeartbeatConfig(100, 0, true);
        service = new HeartbeatService(config);

        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger count = new AtomicInteger(0);

        service.addListener(event -> {
            count.incrementAndGet();
            latch.countDown();
        });

        service.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Should receive at least 3 heartbeats");
        assertTrue(count.get() >= 3);
        assertTrue(service.isRunning());
    }

    @Test
    void shouldProvideCorrectSequenceNumbers() throws InterruptedException {
        HeartbeatConfig config = new HeartbeatConfig(50, 0, true);
        service = new HeartbeatService(config);

        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Long> lastSeq = new AtomicReference<>(0L);

        service.addListener(event -> {
            assertTrue(event.getSequenceNumber() > lastSeq.get(),
                    "Sequence should be monotonically increasing");
            lastSeq.set(event.getSequenceNumber());
            assertNotNull(event.getTimestamp());
            assertEquals(50, event.getIntervalMs());
            latch.countDown();
        });

        service.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void shouldNotStartWhenDisabled() {
        service = new HeartbeatService(HeartbeatConfig.disabled());
        service.start();
        assertFalse(service.isRunning());
    }

    @Test
    void shouldSupportManualBeat() {
        HeartbeatConfig config = new HeartbeatConfig(60_000, 60_000, true);
        service = new HeartbeatService(config);

        AtomicInteger count = new AtomicInteger(0);
        service.addListener(event -> count.incrementAndGet());

        // 不启动定时调度，手动触发
        service.beatNow();
        service.beatNow();

        assertEquals(2, count.get());
        assertEquals(2, service.getSequenceCount());
    }

    @Test
    void shouldHandleListenerException() throws InterruptedException {
        HeartbeatConfig config = new HeartbeatConfig(50, 0, true);
        service = new HeartbeatService(config);

        CountDownLatch latch = new CountDownLatch(1);

        // 第一个监听器抛异常
        service.addListener(event -> { throw new RuntimeException("boom"); });
        // 第二个监听器仍应被调用
        service.addListener(event -> latch.countDown());

        service.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Second listener should still fire despite first one throwing");
    }

    @Test
    void shouldStopCleanly() throws InterruptedException {
        HeartbeatConfig config = new HeartbeatConfig(50, 0, true);
        service = new HeartbeatService(config);
        service.addListener(event -> {});

        service.start();
        assertTrue(service.isRunning());

        service.stop();
        assertFalse(service.isRunning());

        long countAfterStop = service.getSequenceCount();
        Thread.sleep(200);
        assertEquals(countAfterStop, service.getSequenceCount(), "No more beats after stop");
    }

    @Test
    void shouldRemoveListener() {
        service = new HeartbeatService();
        AtomicInteger count = new AtomicInteger(0);
        HeartbeatListener listener = event -> count.incrementAndGet();

        service.addListener(listener);
        assertEquals(1, service.getListenerCount());

        service.removeListener(listener);
        assertEquals(0, service.getListenerCount());

        service.beatNow();
        assertEquals(0, count.get());
    }
}

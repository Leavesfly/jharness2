package io.leavesfly.jharness2.core.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EngineEventBusTest {

    private EngineEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EngineEventBus();
    }

    @AfterEach
    void tearDown() {
        eventBus.shutdown();
    }

    @Test
    void globalListenerShouldReceiveAllEvents() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

        eventBus.subscribe(event -> {
            received.add(event.getEventType());
            latch.countDown();
        });

        eventBus.publish(new EngineCreatedEvent("u1", "s1", "gpt-4"));
        eventBus.publish(new EngineEvictedEvent("u1", "s1", "idle_timeout", 100, 200));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, received.size());
        assertTrue(received.contains("engine.created"));
        assertTrue(received.contains("engine.evicted"));
    }

    @Test
    void typedListenerShouldOnlyReceiveMatchingEvents() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

        eventBus.subscribe("engine.created", event -> {
            received.add(event.getEventType());
            latch.countDown();
        });

        eventBus.publish(new EngineEvictedEvent("u1", "s1", "idle_timeout", 100, 200));
        eventBus.publish(new EngineCreatedEvent("u1", "s1", "gpt-4"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, received.size());
        assertEquals("engine.created", received.get(0));
    }

    @Test
    void publishSyncShouldDeliverImmediately() {
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        eventBus.subscribe(event -> received.add(event.getEventType()));

        eventBus.publishSync(new EngineCreatedEvent("u1", "s1", "gpt-4"));

        // 同步发布，应立即可见
        assertEquals(1, received.size());
    }

    @Test
    void failingListenerShouldNotAffectOthers() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

        // 第一个监听器抛异常
        eventBus.subscribe(event -> { throw new RuntimeException("boom"); });
        // 第二个监听器正常
        eventBus.subscribe(event -> {
            received.add(event.getEventType());
            latch.countDown();
        });

        eventBus.publish(new EngineCreatedEvent("u1", "s1", "gpt-4"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, received.size());
    }

    @Test
    void unsubscribeShouldStopDelivery() {
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        EngineEventListener listener = event -> received.add(event.getEventType());

        eventBus.subscribe(listener);
        eventBus.publishSync(new EngineCreatedEvent("u1", "s1", "gpt-4"));
        assertEquals(1, received.size());

        eventBus.unsubscribe(listener);
        eventBus.publishSync(new EngineCreatedEvent("u2", "s2", "gpt-4"));
        assertEquals(1, received.size()); // 不应再收到
    }
}

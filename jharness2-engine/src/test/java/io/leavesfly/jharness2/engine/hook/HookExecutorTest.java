package io.leavesfly.jharness2.engine.hook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HookExecutorTest {

    private HookExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new HookExecutor();
    }

    @Test
    void shouldRegisterAndFireHook() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> capturedPayload = new AtomicReference<>();

        HookHandler handler = (event, payload) -> {
            capturedPayload.set(payload);
            latch.countDown();
        };

        executor.register(HookEvent.USER_PROMPT_SUBMIT, handler);
        assertEquals(1, executor.handlerCount(HookEvent.USER_PROMPT_SUBMIT));

        Map<String, Object> payload = Map.of("prompt", "test");
        executor.fire(HookEvent.USER_PROMPT_SUBMIT, payload).get(2, TimeUnit.SECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Hook should fire");
        assertNotNull(capturedPayload.get());
        assertEquals("test", capturedPayload.get().get("prompt"));
    }

    @Test
    void shouldSupportMultipleHandlersForSameEvent() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger callCount = new AtomicInteger(0);

        HookHandler handler1 = (event, payload) -> {
            callCount.incrementAndGet();
            latch.countDown();
        };
        HookHandler handler2 = (event, payload) -> {
            callCount.incrementAndGet();
            latch.countDown();
        };

        executor.register(HookEvent.SESSION_START, handler1);
        executor.register(HookEvent.SESSION_START, handler2);

        assertEquals(2, executor.handlerCount(HookEvent.SESSION_START));

        executor.fire(HookEvent.SESSION_START, Map.of()).get(2, TimeUnit.SECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Both handlers should fire");
        assertEquals(2, callCount.get());
    }

    @Test
    void shouldUnregisterHandler() {
        HookHandler handler = (event, payload) -> {};

        executor.register(HookEvent.STOP, handler);
        assertEquals(1, executor.handlerCount(HookEvent.STOP));

        executor.unregister(HookEvent.STOP, handler);
        assertEquals(0, executor.handlerCount(HookEvent.STOP));
    }

    @Test
    void shouldHandleUnregisterNonExistentHandler() {
        HookHandler handler = (event, payload) -> {};
        assertDoesNotThrow(() -> executor.unregister(HookEvent.STOP, handler));
        assertEquals(0, executor.handlerCount(HookEvent.STOP));
    }

    @Test
    void shouldReturnZeroForUnregisteredEvent() {
        assertEquals(0, executor.handlerCount(HookEvent.NOTIFICATION));
    }

    @Test
    void shouldCalculateTotalHandlers() {
        HookHandler handler1 = (event, payload) -> {};
        HookHandler handler2 = (event, payload) -> {};
        HookHandler handler3 = (event, payload) -> {};

        executor.register(HookEvent.SESSION_START, handler1);
        executor.register(HookEvent.SESSION_START, handler2);
        executor.register(HookEvent.STOP, handler3);

        assertEquals(3, executor.totalHandlers());
    }

    @Test
    void shouldHandleHandlerException() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HookHandler failingHandler = (event, payload) -> {
            throw new RuntimeException("Handler error");
        };
        HookHandler successHandler = (event, payload) -> {
            latch.countDown();
        };

        executor.register(HookEvent.PRE_TOOL_USE, failingHandler);
        executor.register(HookEvent.PRE_TOOL_USE, successHandler);

        executor.fire(HookEvent.PRE_TOOL_USE, Map.of()).get(2, TimeUnit.SECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Other handlers should still fire");
    }

    @Test
    void shouldFireDifferentEventsIndependently() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        executor.register(HookEvent.SESSION_START, (event, payload) -> latch1.countDown());
        executor.register(HookEvent.SESSION_END, (event, payload) -> latch2.countDown());

        executor.fire(HookEvent.SESSION_START, Map.of()).get(2, TimeUnit.SECONDS);

        assertTrue(latch1.await(2, TimeUnit.SECONDS), "SESSION_START should fire");
        assertFalse(latch2.await(500, TimeUnit.MILLISECONDS), "SESSION_END should not fire yet");

        executor.fire(HookEvent.SESSION_END, Map.of()).get(2, TimeUnit.SECONDS);
        assertTrue(latch2.await(2, TimeUnit.SECONDS), "SESSION_END should fire");
    }

    @Test
    void shouldHandleEmptyPayload() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        executor.register(HookEvent.STOP, (event, payload) -> {
            assertNotNull(payload);
            assertTrue(payload.isEmpty());
            latch.countDown();
        });

        executor.fire(HookEvent.STOP, Map.of()).get(2, TimeUnit.SECONDS);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
}

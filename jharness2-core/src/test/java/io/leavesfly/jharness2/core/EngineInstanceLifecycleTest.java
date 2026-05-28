package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.engine.QueryEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EngineInstanceLifecycleTest {

    private EngineInstance createInstance() {
        QueryEngine engine = new QueryEngine(null, null, "test", 5);
        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        return new EngineInstance(engine, context);
    }

    @Test
    void newInstanceShouldBeInRunningState() {
        EngineInstance instance = createInstance();
        assertTrue(instance.isRunning());
        assertFalse(instance.isClosed());
        assertEquals(EngineLifecycleState.RUNNING, instance.getLifecycleState());
    }

    @Test
    void getEngineShouldThrowWhenNotRunning() {
        EngineInstance instance = createInstance();
        instance.close();
        assertThrows(IllegalStateException.class, instance::getEngine);
    }

    @Test
    void closeShouldTransitionToClosed() {
        EngineInstance instance = createInstance();
        instance.close();
        assertTrue(instance.isClosed());
        assertFalse(instance.isRunning());
    }

    @Test
    void doubleCloseShouldBeIdempotent() {
        EngineInstance instance = createInstance();
        instance.close();
        assertDoesNotThrow(instance::close);
        assertTrue(instance.isClosed());
    }

    @Test
    void acquireAndReleaseRequestShouldTrackCount() {
        EngineInstance instance = createInstance();
        assertEquals(0, instance.getActiveRequestCount());
        instance.acquireRequest();
        assertEquals(1, instance.getActiveRequestCount());
        instance.acquireRequest();
        assertEquals(2, instance.getActiveRequestCount());
        instance.releaseRequest();
        assertEquals(1, instance.getActiveRequestCount());
        instance.releaseRequest();
        assertEquals(0, instance.getActiveRequestCount());
    }

    @Test
    void gracefulCloseShouldWaitForActiveRequests() throws Exception {
        EngineInstance instance = createInstance();
        instance.acquireRequest();

        CompletableFuture<Void> closeFuture = instance.gracefulClose(Duration.ofSeconds(2));

        // 引擎应该处于 SHUTTING_DOWN 状态
        assertEquals(EngineLifecycleState.SHUTTING_DOWN, instance.getLifecycleState());

        // 模拟请求完成
        Thread.sleep(200);
        instance.releaseRequest();

        // 等待关闭完成
        closeFuture.get(5, TimeUnit.SECONDS);
        assertTrue(instance.isClosed());
    }

    @Test
    void gracefulCloseShouldTimeoutAndForceClose() throws Exception {
        EngineInstance instance = createInstance();
        instance.acquireRequest();

        // 使用很短的超时，不释放请求
        CompletableFuture<Void> closeFuture = instance.gracefulClose(Duration.ofMillis(200));
        closeFuture.get(5, TimeUnit.SECONDS);

        assertTrue(instance.isClosed());
    }

    @Test
    void gracefulCloseWhenAlreadyShuttingDownShouldReturnImmediately() throws Exception {
        EngineInstance instance = createInstance();
        CompletableFuture<Void> first = instance.gracefulClose();
        CompletableFuture<Void> second = instance.gracefulClose();

        // 第二次调用应立即完成
        second.get(1, TimeUnit.SECONDS);
    }

    @Test
    void getEngineShouldUpdateLastAccessedAt() throws Exception {
        EngineInstance instance = createInstance();
        var firstAccess = instance.getLastAccessedAt();
        Thread.sleep(10);
        instance.getEngine();
        var secondAccess = instance.getLastAccessedAt();
        assertTrue(secondAccess.isAfter(firstAccess));
    }
}

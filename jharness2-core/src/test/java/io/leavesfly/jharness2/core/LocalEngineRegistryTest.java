package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.QueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class LocalEngineRegistryTest {

    private LocalEngineRegistry registry;
    private EngineConfig config;

    @BeforeEach
    void setUp() {
        config = new EngineConfig();
        config.setMaxEnginesPerUser(3);
        config.setMaxTotalEngines(10);
        config.setEngineIdleTimeoutMinutes(30);

        EngineFactory factory = new StubEngineFactory();
        registry = new LocalEngineRegistry(factory, config);
        registry.init();
    }

    @Test
    void getOrCreateShouldCreateNewEngine() {
        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        EngineInstance instance = registry.getOrCreate(context);
        assertNotNull(instance);
        assertEquals("user1", instance.getUserContext().getUserId());
    }

    @Test
    void getOrCreateShouldReturnCachedInstance() {
        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        EngineInstance first = registry.getOrCreate(context);
        EngineInstance second = registry.getOrCreate(context);
        assertSame(first, second);
    }

    @Test
    void getOrCreateShouldEnforcePerUserLimit() {
        for (int i = 0; i < 3; i++) {
            UserContext ctx = new UserContext("user1", "s" + i, Path.of("/tmp"), "model", "key", "url");
            registry.getOrCreate(ctx);
        }
        UserContext overflow = new UserContext("user1", "s99", Path.of("/tmp"), "model", "key", "url");
        assertThrows(EngineLimitException.class, () -> registry.getOrCreate(overflow));
    }

    @Test
    void differentUsersShouldHaveIndependentLimits() {
        for (int i = 0; i < 3; i++) {
            UserContext ctx = new UserContext("user1", "s" + i, Path.of("/tmp"), "model", "key", "url");
            registry.getOrCreate(ctx);
        }
        UserContext otherUser = new UserContext("user2", "s0", Path.of("/tmp"), "model", "key", "url");
        assertDoesNotThrow(() -> registry.getOrCreate(otherUser));
    }

    @Test
    void getShouldReturnNullForUnknownSession() {
        assertNull(registry.get("nobody", "nosession"));
    }

    @Test
    void evictShouldRemoveEngine() {
        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        registry.getOrCreate(context);
        assertNotNull(registry.get("user1", "s1"));
        registry.evict("user1", "s1");
        assertNull(registry.get("user1", "s1"));
    }

    @Test
    void activeEngineCountShouldReflectState() {
        assertEquals(0, registry.activeEngineCount());
        UserContext ctx1 = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        UserContext ctx2 = new UserContext("user2", "s2", Path.of("/tmp"), "model", "key", "url");
        registry.getOrCreate(ctx1);
        registry.getOrCreate(ctx2);
        assertEquals(2, registry.activeEngineCount());
    }

    @Test
    void userEngineCountShouldBeAccurate() {
        assertEquals(0, registry.userEngineCount("user1"));
        UserContext ctx1 = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        UserContext ctx2 = new UserContext("user1", "s2", Path.of("/tmp"), "model", "key", "url");
        registry.getOrCreate(ctx1);
        registry.getOrCreate(ctx2);
        assertEquals(2, registry.userEngineCount("user1"));
    }

    @Test
    void concurrentGetOrCreateSameKeyShouldCreateSingleEngine() throws Exception {
        CountingEngineFactory factory = new CountingEngineFactory();
        LocalEngineRegistry concurrentRegistry = new LocalEngineRegistry(factory, config);
        concurrentRegistry.init();

        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<EngineInstance>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return concurrentRegistry.getOrCreate(context);
                }));
            }
            start.countDown();

            Set<EngineInstance> distinct = new HashSet<>();
            for (Future<EngineInstance> future : futures) {
                distinct.add(future.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, distinct.size(), "All threads should get the same instance");
            assertEquals(1, factory.createCount.get(), "Factory should be invoked exactly once");
            assertEquals(1, concurrentRegistry.userEngineCount("user1"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentGetOrCreateShouldNotExceedUserQuota() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger rejected = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    UserContext ctx = new UserContext("user1", "cs" + idx,
                            Path.of("/tmp"), "model", "key", "url");
                    try {
                        registry.getOrCreate(ctx);
                        success.incrementAndGet();
                    } catch (EngineLimitException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            assertEquals(3, success.get(), "Quota must not be exceeded under concurrency");
            assertEquals(threads - 3, rejected.get());
            assertEquals(3, registry.userEngineCount("user1"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void getOrCreateShouldRestoreSessionFromPersistence() {
        List<ConversationMessage> history = List.of(
                ConversationMessage.system("restored system"),
                ConversationMessage.user("old question"),
                ConversationMessage.assistant("old answer"));
        SessionPersistenceService persistence = new SessionPersistenceService() {
            @Override
            public void saveSession(String userId, String sessionId, String model,
                                    List<?> messages, long inputTokens, long outputTokens) {
            }

            @Override
            public Optional<PersistedSession> loadSession(String userId, String sessionId) {
                return Optional.of(new PersistedSession(history, 11, 22));
            }
        };

        LocalEngineRegistry restoringRegistry =
                new LocalEngineRegistry(new StubEngineFactory(), config, persistence);
        restoringRegistry.init();

        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        EngineInstance instance = restoringRegistry.getOrCreate(context);

        assertEquals(3, instance.getEngine().getMessages().size(),
                "Cache miss should restore full history from persistence");
        assertEquals(11, instance.getEngine().getCostTracker().getInputTokens());
        assertEquals(22, instance.getEngine().getCostTracker().getOutputTokens());
    }

    /**
     * 简单的 stub 工厂，返回最小可用的 EngineInstance。
     */
    static class StubEngineFactory implements EngineFactory {
        @Override
        public EngineInstance create(UserContext context) {
            QueryEngine engine = new QueryEngine(null, null, "test prompt", 5);
            return new EngineInstance(engine, context);
        }

        @Override
        public EngineInstance restore(UserContext context, List<ConversationMessage> messages,
                                      long inputTokens, long outputTokens) {
            EngineInstance instance = create(context);
            instance.getEngine().loadMessages(messages);
            instance.getEngine().getCostTracker().restore(inputTokens, outputTokens);
            return instance;
        }
    }

    /**
     * 统计创建次数的工厂，用于验证并发 getOrCreate 的原子性。
     */
    static class CountingEngineFactory extends StubEngineFactory {
        final AtomicInteger createCount = new AtomicInteger(0);

        @Override
        public EngineInstance create(UserContext context) {
            createCount.incrementAndGet();
            return super.create(context);
        }
    }
}

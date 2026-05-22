package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.engine.ConversationMessage;
import io.leavesfly.jharness2.engine.QueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

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
        assertThrows(EngineLimitExceededException.class, () -> registry.getOrCreate(overflow));
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
}

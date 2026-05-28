package io.leavesfly.jharness2.core.distributed;

import io.leavesfly.jharness2.core.*;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.QueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DistributedEngineRegistryTest {

    private DistributedEngineRegistry registry;
    private EngineConfig config;
    private InMemoryEngineStateStore stateStore;
    private StubEngineFactory factory;

    @BeforeEach
    void setUp() {
        config = new EngineConfig();
        config.setMaxEnginesPerUser(3);
        config.setMaxTotalEngines(10);
        config.setEngineIdleTimeoutMinutes(30);

        EngineConfig.Distributed distributed = new EngineConfig.Distributed();
        distributed.setEnabled(true);
        distributed.setNodeId("test-node-1");
        distributed.setOwnershipLeaseSeconds(60);
        distributed.setStateTtlMinutes(35);
        distributed.setStateSyncIntervalSeconds(10);
        config.setDistributed(distributed);

        stateStore = new InMemoryEngineStateStore();
        factory = new StubEngineFactory();
        registry = new DistributedEngineRegistry(factory, config, stateStore);
        registry.init();
    }

    @Test
    void getOrCreateShouldCreateNewEngineAndStoreState() {
        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        EngineInstance instance = registry.getOrCreate(context);

        assertNotNull(instance);
        assertEquals("user1", instance.getUserContext().getUserId());
        // State should be saved in store
        assertTrue(stateStore.load("user1:s1").isPresent());
        assertEquals(1, stateStore.countByUser("user1"));
    }

    @Test
    void getOrCreateShouldReturnLocalCachedInstance() {
        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        EngineInstance first = registry.getOrCreate(context);
        EngineInstance second = registry.getOrCreate(context);
        assertSame(first, second);
        // Factory should only be called once
        assertEquals(1, factory.createCount);
    }

    @Test
    void getOrCreateShouldRestoreFromStateStore() {
        // Simulate state left by another node
        EngineState state = new EngineState();
        state.setUserId("user1");
        state.setSessionId("s1");
        state.setModel("model");
        state.setBaseUrl("url");
        state.setApiKey("key");
        state.setWorkspacePath("/tmp");
        state.setMessages(List.of(ConversationMessage.user("hello")));
        state.setInputTokens(100);
        state.setOutputTokens(50);
        state.setCreatedAt(Instant.now());
        state.setLastAccessedAt(Instant.now());
        state.setOwnerNodeId("old-node");

        stateStore.save("user1:s1", state, Duration.ofMinutes(35));
        // Release ownership so current node can acquire
        stateStore.releaseOwnership("user1:s1", "old-node");

        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        EngineInstance instance = registry.getOrCreate(context);

        assertNotNull(instance);
        // Should have called restore instead of create
        assertEquals(1, factory.restoreCount);
        assertEquals(0, factory.createCount);
    }

    @Test
    void getOrCreateShouldThrowWhenOwnedByAnotherNode() {
        EngineState state = new EngineState();
        state.setUserId("user1");
        state.setSessionId("s1");
        state.setMessages(List.of());
        state.setOwnerNodeId("other-node");
        state.setCreatedAt(Instant.now());
        state.setLastAccessedAt(Instant.now());

        stateStore.save("user1:s1", state, Duration.ofMinutes(35));
        // "other-node" owns it
        stateStore.tryAcquireOwnership("user1:s1", "other-node", Duration.ofMinutes(5));

        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        assertThrows(EngineOwnedByOtherNodeException.class, () -> registry.getOrCreate(context));
    }

    @Test
    void getOrCreateShouldEnforceDistributedUserLimit() {
        // Pre-set count in store to max
        for (int i = 0; i < 3; i++) {
            stateStore.incrementUserCount("user1");
        }
        UserContext context = new UserContext("user1", "s99", Path.of("/tmp"), "model", "key", "url");
        assertThrows(EngineLimitException.class, () -> registry.getOrCreate(context));
    }

    @Test
    void evictShouldRemoveFromLocalCacheAndReleaseOwnership() {
        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        registry.getOrCreate(context);

        assertNotNull(registry.get("user1", "s1"));
        registry.evict("user1", "s1");

        assertNull(registry.get("user1", "s1"));
        // State and ownership should be cleaned
        assertFalse(stateStore.load("user1:s1").isPresent());
    }

    @Test
    void syncAllStatesShouldUpdateRedisState() {
        UserContext context = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        registry.getOrCreate(context);

        // Clear state to verify sync re-saves it
        stateStore.remove("user1:s1");
        assertFalse(stateStore.load("user1:s1").isPresent());

        registry.syncAllStates();
        assertTrue(stateStore.load("user1:s1").isPresent());
    }

    @Test
    void userEngineCountShouldUseDistributedCount() {
        assertEquals(0, registry.userEngineCount("user1"));
        UserContext ctx = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        registry.getOrCreate(ctx);
        assertEquals(1, registry.userEngineCount("user1"));
    }

    @Test
    void shutdownShouldSyncAndReleaseAll() {
        UserContext ctx1 = new UserContext("user1", "s1", Path.of("/tmp"), "model", "key", "url");
        UserContext ctx2 = new UserContext("user1", "s2", Path.of("/tmp"), "model", "key", "url");
        registry.getOrCreate(ctx1);
        registry.getOrCreate(ctx2);

        registry.shutdown();

        assertEquals(0, registry.activeEngineCount());
    }

    // --- Test Doubles ---

    static class StubEngineFactory implements EngineFactory {
        int createCount = 0;
        int restoreCount = 0;

        @Override
        public EngineInstance create(UserContext context) {
            createCount++;
            QueryEngine engine = new QueryEngine(null, null, "test", 5);
            engine.setWorkingDirectory(context.getWorkspace());
            return new EngineInstance(engine, context);
        }

        @Override
        public EngineInstance restore(UserContext context, List<ConversationMessage> messages,
                                      long inputTokens, long outputTokens) {
            restoreCount++;
            EngineInstance instance = create(context);
            createCount--; // Don't count the internal create call
            instance.getEngine().loadMessages(messages);
            instance.getEngine().getCostTracker().restore(inputTokens, outputTokens);
            return instance;
        }
    }

    /**
     * In-memory implementation of EngineStateStore for testing (no Redis needed).
     */
    static class InMemoryEngineStateStore implements EngineStateStore {
        private final Map<String, EngineState> states = new ConcurrentHashMap<>();
        private final Map<String, String> owners = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

        @Override
        public void save(String cacheKey, EngineState state, Duration ttl) {
            states.put(cacheKey, state);
        }

        @Override
        public Optional<EngineState> load(String cacheKey) {
            return Optional.ofNullable(states.get(cacheKey));
        }

        @Override
        public void remove(String cacheKey) {
            states.remove(cacheKey);
            owners.remove(cacheKey);
        }

        @Override
        public void touch(String cacheKey, Duration ttl) {
            // no-op in memory
        }

        @Override
        public int countByUser(String userId) {
            AtomicInteger count = counts.get(userId);
            return count != null ? count.get() : 0;
        }

        @Override
        public void incrementUserCount(String userId) {
            counts.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();
        }

        @Override
        public void decrementUserCount(String userId) {
            AtomicInteger count = counts.get(userId);
            if (count != null && count.get() > 0) {
                count.decrementAndGet();
            }
        }

        @Override
        public boolean tryAcquireOwnership(String cacheKey, String nodeId, Duration lease) {
            String existing = owners.putIfAbsent(cacheKey, nodeId);
            if (existing == null) return true;
            if (existing.equals(nodeId)) return true;
            return false;
        }

        @Override
        public void releaseOwnership(String cacheKey, String nodeId) {
            owners.remove(cacheKey, nodeId);
        }
    }
}

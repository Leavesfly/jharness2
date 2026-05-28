package io.leavesfly.jharness2.storage.checkpoint;

import io.leavesfly.jharness2.core.EngineInstance;
import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.checkpoint.CheckpointConfig;
import io.leavesfly.jharness2.core.checkpoint.CheckpointData;
import io.leavesfly.jharness2.core.checkpoint.SessionCheckpointService;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SessionCheckpointServiceTest {

    private SessionCheckpointService service;
    private InMemoryCheckpointStore store;
    private CheckpointConfig config;

    @BeforeEach
    void setUp() {
        config = new CheckpointConfig();
        config.setEnabled(true);
        config.setIntervalTurns(2);
        config.setMaxCheckpointsPerSession(3);
        store = new InMemoryCheckpointStore(config.getMaxCheckpointsPerSession());
        service = new SessionCheckpointService(store, config);
    }

    private EngineInstance createInstanceWithMessages(String userId, String sessionId, int messageCount) {
        QueryEngine engine = new QueryEngine(null, null, "test", 5);
        if (messageCount > 0) {
            java.util.List<ConversationMessage> messages = new java.util.ArrayList<>();
            for (int i = 0; i < messageCount; i++) {
                messages.add(ConversationMessage.user("msg" + i));
            }
            engine.loadMessages(messages);
        }
        UserContext context = new UserContext(userId, sessionId, Path.of("/tmp"), "model", "key", "url");
        return new EngineInstance(engine, context);
    }

    @Test
    void shouldNotCheckpointWhenDisabled() {
        config.setEnabled(false);
        EngineInstance instance = createInstanceWithMessages("u1", "s1", 3);
        service.onTurnCompleted(instance);
        service.onTurnCompleted(instance);
        Optional<CheckpointData> result = service.loadLatest("u1", "s1");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldCheckpointAtConfiguredInterval() {
        EngineInstance instance = createInstanceWithMessages("u1", "s1", 3);

        // Turn 1: 不触发（interval=2）
        service.onTurnCompleted(instance);
        assertTrue(service.loadLatest("u1", "s1").isEmpty());

        // Turn 2: 触发
        service.onTurnCompleted(instance);
        Optional<CheckpointData> result = service.loadLatest("u1", "s1");
        assertTrue(result.isPresent());
        assertEquals(3, result.get().getMessages().size());
    }

    @Test
    void manualCheckpointShouldWork() {
        EngineInstance instance = createInstanceWithMessages("u1", "s1", 5);
        service.checkpoint(instance);
        Optional<CheckpointData> result = service.loadLatest("u1", "s1");
        assertTrue(result.isPresent());
        assertEquals(5, result.get().getMessages().size());
    }

    @Test
    void shouldCheckpointEvenWithOnlySystemMessage() {
        EngineInstance instance = createInstanceWithMessages("u1", "s1", 0);
        service.checkpoint(instance);
        Optional<CheckpointData> result = service.loadLatest("u1", "s1");
        assertTrue(result.isPresent());
        assertEquals(1, result.get().getMessages().size());
    }

    @Test
    void cleanupShouldRemoveAllCheckpoints() {
        EngineInstance instance = createInstanceWithMessages("u1", "s1", 3);
        service.checkpoint(instance);
        assertTrue(service.loadLatest("u1", "s1").isPresent());

        service.cleanup("u1", "s1");
        assertTrue(service.loadLatest("u1", "s1").isEmpty());
    }

    @Test
    void storeShouldRespectMaxPerSession() {
        EngineInstance instance = createInstanceWithMessages("u1", "s1", 1);

        for (int i = 0; i < 5; i++) {
            service.checkpoint(instance);
        }

        Optional<CheckpointData> result = service.loadLatest("u1", "s1");
        assertTrue(result.isPresent());
    }
}

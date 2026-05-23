package io.leavesfly.jharness2.engine;

import io.leavesfly.jharness2.engine.agent.AgentOrchestrator;
import io.leavesfly.jharness2.engine.cron.CronScheduler;
import io.leavesfly.jharness2.engine.mcp.McpManager;
import io.leavesfly.jharness2.engine.skill.SkillRegistry;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import io.leavesfly.jharness2.engine.task.BackgroundTaskManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class EngineContextTest {

    @Test
    void shouldSetAndGetAgentOrchestrator() {
        EngineContext context = new EngineContext();
        AgentOrchestrator orchestrator = new AgentOrchestrator(new StubLlmClient());

        context.setAgentOrchestrator(orchestrator);
        Optional<AgentOrchestrator> retrieved = context.getAgentOrchestrator();

        assertTrue(retrieved.isPresent());
        assertSame(orchestrator, retrieved.get());
    }

    @Test
    void shouldReturnEmptyWhenAgentOrchestratorNotSet() {
        EngineContext context = new EngineContext();
        assertFalse(context.getAgentOrchestrator().isPresent());
    }

    @Test
    void shouldSetAndGetSkillRegistry() {
        EngineContext context = new EngineContext();
        SkillRegistry skillRegistry = new SkillRegistry();

        context.setSkillRegistry(skillRegistry);
        Optional<SkillRegistry> retrieved = context.getSkillRegistry();

        assertTrue(retrieved.isPresent());
        assertSame(skillRegistry, retrieved.get());
    }

    @Test
    void shouldSetAndGetMcpManager() {
        EngineContext context = new EngineContext();
        McpManager mcpManager = new McpManager();

        context.setMcpManager(mcpManager);
        Optional<McpManager> retrieved = context.getMcpManager();

        assertTrue(retrieved.isPresent());
        assertSame(mcpManager, retrieved.get());
    }

    @Test
    void shouldSetAndGetCronScheduler() {
        EngineContext context = new EngineContext();
        CronScheduler cronScheduler = new CronScheduler();

        context.setCronScheduler(cronScheduler);
        Optional<CronScheduler> retrieved = context.getCronScheduler();

        assertTrue(retrieved.isPresent());
        assertSame(cronScheduler, retrieved.get());
    }

    @Test
    void shouldSetAndGetBackgroundTaskManager() {
        EngineContext context = new EngineContext();
        BackgroundTaskManager taskManager = new BackgroundTaskManager(Path.of("/tmp/tasks"));

        context.setBackgroundTaskManager(taskManager);
        Optional<BackgroundTaskManager> retrieved = context.getBackgroundTaskManager();

        assertTrue(retrieved.isPresent());
        assertSame(taskManager, retrieved.get());
    }

    @Test
    void shouldRegisterAndRetrieveExtension() {
        EngineContext context = new EngineContext();
        CustomExtension extension = new CustomExtension("test-value");

        context.registerExtension(CustomExtension.class, extension);
        Optional<CustomExtension> retrieved = context.getExtension(CustomExtension.class);

        assertTrue(retrieved.isPresent());
        assertSame(extension, retrieved.get());
        assertEquals("test-value", retrieved.get().getValue());
    }

    @Test
    void shouldReturnEmptyForUnregisteredExtension() {
        EngineContext context = new EngineContext();
        Optional<CustomExtension> retrieved = context.getExtension(CustomExtension.class);

        assertFalse(retrieved.isPresent());
    }

    @Test
    void shouldCloseAllSubsystems() {
        AtomicBoolean mcpClosed = new AtomicBoolean(false);
        AtomicBoolean agentShutdown = new AtomicBoolean(false);
        AtomicBoolean taskManagerShutdown = new AtomicBoolean(false);
        AtomicBoolean cronShutdown = new AtomicBoolean(false);

        EngineContext context = new EngineContext();
        context.setMcpManager(new McpManager() {
            @Override
            public void close() {
                mcpClosed.set(true);
                super.close();
            }
        });
        context.setAgentOrchestrator(new AgentOrchestrator(new StubLlmClient()) {
            @Override
            public void shutdown() {
                agentShutdown.set(true);
                super.shutdown();
            }
        });
        context.setBackgroundTaskManager(new BackgroundTaskManager(Path.of("/tmp/tasks")) {
            @Override
            public void shutdown() {
                taskManagerShutdown.set(true);
                super.shutdown();
            }
        });
        context.setCronScheduler(new CronScheduler() {
            @Override
            public void shutdown() {
                cronShutdown.set(true);
                super.shutdown();
            }
        });

        context.close();

        assertTrue(mcpClosed.get(), "McpManager should be closed");
        assertTrue(agentShutdown.get(), "AgentOrchestrator should be shutdown");
        assertTrue(taskManagerShutdown.get(), "BackgroundTaskManager should be shutdown");
        assertTrue(cronShutdown.get(), "CronScheduler should be shutdown");
    }

    @Test
    void shouldHandleNullSubsystemsGracefully() {
        EngineContext context = new EngineContext();
        // No subsystems set
        assertDoesNotThrow(() -> context.close());
    }

    // --- Stub Implementations ---

    private static class CustomExtension {
        private final String value;

        CustomExtension(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private static class StubLlmClient implements LlmClient {
        @Override
        public LlmResponse chatStream(
                List<ConversationMessage> messages,
                List<java.util.Map<String, Object>> tools,
                Consumer<StreamEvent> eventConsumer) {
            return new LlmResponse("", null, 0, 0);
        }

        @Override
        public void close() {
        }
    }
}

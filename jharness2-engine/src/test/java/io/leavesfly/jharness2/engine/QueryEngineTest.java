package io.leavesfly.jharness2.engine;

import io.leavesfly.jharness2.engine.compaction.CompactionStrategy;
import io.leavesfly.jharness2.engine.hook.HookEvent;
import io.leavesfly.jharness2.engine.hook.HookExecutor;
import io.leavesfly.jharness2.engine.stream.AssistantTurnComplete;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import io.leavesfly.jharness2.engine.stream.UsageReport;
import io.leavesfly.jharness2.engine.tool.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class QueryEngineTest {

    private QueryEngine engine;
    private StubLlmClient llmClient;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        llmClient = new StubLlmClient();
        engine = new QueryEngine(llmClient, toolRegistry, "You are a helpful assistant", 10);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void shouldHandlePureTextResponse() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean receivedTurnComplete = new AtomicBoolean(false);

        llmClient.setResponse(new LlmResponse("Hello! How can I help you?", null, 10, 20));

        engine.submitMessage("Hi", event -> {
            if (event instanceof AssistantTurnComplete) {
                receivedTurnComplete.set(true);
                latch.countDown();
            }
        }).get(5, TimeUnit.SECONDS);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should complete turn");
        assertTrue(receivedTurnComplete.get(), "Should receive turn complete event");

        List<ConversationMessage> messages = engine.getMessages();
        assertEquals(3, messages.size()); // system + user + assistant
        assertEquals(ConversationMessage.Role.USER, messages.get(1).getRole());
        assertEquals("Hi", messages.get(1).getContent());
        assertEquals(ConversationMessage.Role.ASSISTANT, messages.get(2).getRole());
        assertEquals("Hello! How can I help you?", messages.get(2).getContent());
    }

    @Test
    void shouldHandleSingleToolCallRound() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger turnCount = new AtomicInteger(0);

        ConversationMessage.ToolCall toolCall = new ConversationMessage.ToolCall(
                "call_1", "function",
                new ConversationMessage.FunctionCall("echo", "{\"text\":\"hello\"}")
        );

        llmClient.addResponse(new LlmResponse("", List.of(toolCall), 15, 30));
        llmClient.addResponse(new LlmResponse("Done!", null, 10, 15));

        engine.submitMessage("Use echo tool", event -> {
            if (event instanceof AssistantTurnComplete) {
                turnCount.incrementAndGet();
                latch.countDown();
            }
        }).get(5, TimeUnit.SECONDS);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should complete");
        assertEquals(1, turnCount.get(), "Should have 1 complete turn");

        List<ConversationMessage> messages = engine.getMessages();
        assertTrue(messages.size() >= 4, "Should have system + user + assistant(tool) + tool_result + assistant(final)");
    }

    @Test
    void shouldRespectMaxTurns() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger turnCount = new AtomicInteger(0);

        ConversationMessage.ToolCall toolCall = new ConversationMessage.ToolCall(
                "call_1", "function",
                new ConversationMessage.FunctionCall("loop", "{}")
        );

        llmClient.setAlwaysReturnToolCalls(toolCall);

        QueryEngine limitedEngine = new QueryEngine(llmClient, toolRegistry, "Test", 3);
        limitedEngine.submitMessage("Start", event -> {
            if (event instanceof AssistantTurnComplete) {
                turnCount.incrementAndGet();
            }
        }).get(10, TimeUnit.SECONDS);

        // After maxTurns (3), the loop should exit even with tool calls
        assertTrue(turnCount.get() <= 3, "Should respect max turns limit");
        limitedEngine.close();
    }

    @Test
    void shouldCancelExecution() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);

        llmClient.setOnCall(() -> {
            startLatch.countDown();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        engine.submitMessage("Long running", event -> {}).thenRun(() -> {
            cancelLatch.countDown();
        });

        assertTrue(startLatch.await(2, TimeUnit.SECONDS), "Should start execution");
        engine.cancel();

        assertTrue(cancelLatch.await(5, TimeUnit.SECONDS), "Should cancel execution");
    }

    @Test
    void shouldLoadAndRetrieveMessages() {
        List<ConversationMessage> history = new ArrayList<>();
        history.add(ConversationMessage.system("Previous context"));
        history.add(ConversationMessage.user("Previous question"));
        history.add(ConversationMessage.assistant("Previous answer"));

        engine.loadMessages(history);

        List<ConversationMessage> retrieved = engine.getMessages();
        assertEquals(3, retrieved.size());
        assertEquals("Previous context", retrieved.get(0).getContent());
        assertEquals("Previous question", retrieved.get(1).getContent());
        assertEquals("Previous answer", retrieved.get(2).getContent());
    }

    @Test
    void shouldTriggerHooks() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HookExecutor hookExecutor = new HookExecutor();
        AtomicBoolean hookFired = new AtomicBoolean(false);

        hookExecutor.register(HookEvent.USER_PROMPT_SUBMIT, (event, payload) -> {
            hookFired.set(true);
            latch.countDown();
        });

        engine.setHookExecutor(hookExecutor);
        llmClient.setResponse(new LlmResponse("OK", null, 5, 10));

        engine.submitMessage("Test prompt", event -> {}).get(5, TimeUnit.SECONDS);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Hook should fire");
        assertTrue(hookFired.get(), "USER_PROMPT_SUBMIT hook should be triggered");
    }

    @Test
    void shouldCallSessionPersister() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean persisted = new AtomicBoolean(false);

        SessionPersister persister = messages -> {
            persisted.set(true);
            latch.countDown();
        };

        engine.setSessionPersister(persister);
        llmClient.setResponse(new LlmResponse("Response", null, 5, 10));

        engine.submitMessage("Test", event -> {}).get(5, TimeUnit.SECONDS);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should persist session");
        assertTrue(persisted.get(), "SessionPersister should be called");
    }

    @Test
    void shouldTrackCost() throws Exception {
        llmClient.setResponseWithUsage(new LlmResponse("Answer", null, 100, 200), 100, 200);

        engine.submitMessage("Question", event -> {}).get(5, TimeUnit.SECONDS);

        CostTracker tracker = engine.getCostTracker();
        assertEquals(100, tracker.getInputTokens());
        assertEquals(200, tracker.getOutputTokens());
        assertEquals(300, tracker.getTotalTokens());
    }

    @Test
    void shouldCloseResources() {
        AtomicBoolean llmClosed = new AtomicBoolean(false);
        LlmClient closableLlm = new StubLlmClient() {
            @Override
            public void close() {
                llmClosed.set(true);
            }
        };

        QueryEngine testEngine = new QueryEngine(closableLlm, toolRegistry, "Test", 5);
        testEngine.close();

        assertTrue(llmClosed.get(), "LLM client should be closed");
    }

    // --- Stub Implementations ---

    private static class StubLlmClient implements LlmClient {
        private LlmResponse singleResponse;
        private List<LlmResponse> responseQueue = new ArrayList<>();
        private int responseIndex = 0;
        private ConversationMessage.ToolCall alwaysToolCall;
        private Runnable onCallCallback;
        private long usageInputTokens = 0;
        private long usageOutputTokens = 0;

        void setResponse(LlmResponse response) {
            this.singleResponse = response;
        }

        void setResponseWithUsage(LlmResponse response, long inputTokens, long outputTokens) {
            this.singleResponse = response;
            this.usageInputTokens = inputTokens;
            this.usageOutputTokens = outputTokens;
        }

        void addResponse(LlmResponse response) {
            this.responseQueue.add(response);
        }

        void setAlwaysReturnToolCalls(ConversationMessage.ToolCall toolCall) {
            this.alwaysToolCall = toolCall;
        }

        void setOnCall(Runnable callback) {
            this.onCallCallback = callback;
        }

        @Override
        public LlmResponse chatStream(List<ConversationMessage> messages, List<Map<String, Object>> tools, Consumer<StreamEvent> eventConsumer) {
            if (onCallCallback != null) {
                onCallCallback.run();
            }

            // Send usage report if configured
            if (usageInputTokens > 0 || usageOutputTokens > 0) {
                long total = usageInputTokens + usageOutputTokens;
                double cost = (usageInputTokens * 0.001 + usageOutputTokens * 0.002) / 1000.0;
                eventConsumer.accept(new UsageReport(usageInputTokens, usageOutputTokens, total, cost));
            }

            if (alwaysToolCall != null) {
                return new LlmResponse("", List.of(alwaysToolCall), 10, 20);
            }

            if (!responseQueue.isEmpty() && responseIndex < responseQueue.size()) {
                return responseQueue.get(responseIndex++);
            }

            if (singleResponse != null) {
                return singleResponse;
            }

            return new LlmResponse("Default response", null, 5, 10);
        }

        @Override
        public void close() {
        }
    }
}

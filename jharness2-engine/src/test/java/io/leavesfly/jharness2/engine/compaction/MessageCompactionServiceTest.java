package io.leavesfly.jharness2.engine.compaction;

import io.leavesfly.jharness2.engine.ConversationMessage;
import io.leavesfly.jharness2.engine.LlmClient;
import io.leavesfly.jharness2.engine.LlmResponse;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class MessageCompactionServiceTest {

    @Test
    void shouldNotNeedCompactionWhenUnderMaxMessages() {
        MessageCompactionService service = new MessageCompactionService(20, 5, 32000);
        List<ConversationMessage> messages = createMessages(10);

        assertFalse(service.needsCompaction(messages));
    }

    @Test
    void shouldNeedCompactionWhenExceedsMaxMessages() {
        MessageCompactionService service = new MessageCompactionService(20, 5, 32000);
        List<ConversationMessage> messages = createMessages(25);

        assertTrue(service.needsCompaction(messages));
    }

    @Test
    void shouldNeedCompactionWhenExceedsTokenBudget() {
        // Create messages with enough content to exceed token budget
        MessageCompactionService service = new MessageCompactionService(100, 5, 100);
        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.system("System"));
        // Add messages with enough content to exceed 100 token budget
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longContent.append("This is a long message to consume token budget. ");
        }
        messages.add(ConversationMessage.user(longContent.toString()));

        assertTrue(service.needsCompaction(messages));
    }

    @Test
    void shouldCompactMessages() {
        MessageCompactionService service = new MessageCompactionService(10, 3, 32000);
        List<ConversationMessage> messages = createMessages(15);

        StubLlmClient llmClient = new StubLlmClient();
        llmClient.setResponse(new LlmResponse("Summary of conversation", null, 50, 100));

        List<ConversationMessage> compacted = service.compact(messages, llmClient);

        assertNotNull(compacted);
        assertTrue(compacted.size() < messages.size(), "Compacted messages should be fewer");
        assertEquals(ConversationMessage.Role.SYSTEM, compacted.get(0).getRole(), "System prompt should be preserved");
    }

    @Test
    void shouldPreserveSystemPromptAndRecentMessages() {
        MessageCompactionService service = new MessageCompactionService(10, 2, 32000);
        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.system("System prompt"));
        for (int i = 0; i < 12; i++) {
            messages.add(ConversationMessage.user("User message " + i));
            messages.add(ConversationMessage.assistant("Assistant response " + i));
        }

        StubLlmClient llmClient = new StubLlmClient();
        llmClient.setResponse(new LlmResponse("Conversation summary", null, 50, 100));

        List<ConversationMessage> compacted = service.compact(messages, llmClient);

        assertEquals(ConversationMessage.Role.SYSTEM, compacted.get(0).getRole());
        assertEquals("System prompt", compacted.get(0).getContent());

        // Should have system + summary user + summary assistant + 2 recent pairs
        assertTrue(compacted.size() <= 7, "Should preserve system + summary + recent messages");
    }

    @Test
    void shouldReturnOriginalMessagesWhenNoCompactionNeeded() {
        MessageCompactionService service = new MessageCompactionService(20, 5, 32000);
        List<ConversationMessage> messages = createMessages(5);

        StubLlmClient llmClient = new StubLlmClient();
        List<ConversationMessage> result = service.compact(messages, llmClient);

        assertSame(messages, result, "Should return original list when no compaction needed");
    }

    @Test
    void shouldHandleEmptyMessages() {
        MessageCompactionService service = new MessageCompactionService(10, 2, 32000);
        List<ConversationMessage> messages = new ArrayList<>();

        StubLlmClient llmClient = new StubLlmClient();
        List<ConversationMessage> result = service.compact(messages, llmClient);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleFallbackWhenLlmFails() {
        MessageCompactionService service = new MessageCompactionService(10, 2, 32000);
        List<ConversationMessage> messages = createMessages(15);

        FailingLlmClient llmClient = new FailingLlmClient();

        List<ConversationMessage> compacted = service.compact(messages, llmClient);

        assertNotNull(compacted);
        assertTrue(compacted.size() < messages.size());
        // Check that fallback summary is used
        boolean hasFallback = compacted.stream()
                .anyMatch(msg -> msg.getContent() != null && msg.getContent().contains("[Previous conversation"));
        assertTrue(hasFallback, "Should use fallback summary when LLM fails");
    }

    @Test
    void shouldRespectCustomTokenBudget() {
        MessageCompactionService service = new MessageCompactionService(100, 5, 500);
        List<ConversationMessage> messages = createLongMessages(10);

        assertTrue(service.needsCompaction(messages), "Should need compaction due to token budget");
    }

    @Test
    void shouldSupportWithSystemPromptTokens() {
        MessageCompactionService service = new MessageCompactionService(100, 5, 500);
        service.withSystemPromptTokens(450);

        // Create messages that with system prompt tokens will exceed budget
        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.system("System"));
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            content.append("Message content to add tokens. ");
        }
        messages.add(ConversationMessage.user(content.toString()));

        assertTrue(service.needsCompaction(messages), "Should account for system prompt tokens");
    }

    // --- Helper Methods ---

    private List<ConversationMessage> createMessages(int count) {
        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.system("System prompt"));
        for (int i = 0; i < count - 1; i++) {
            if (i % 2 == 0) {
                messages.add(ConversationMessage.user("User message " + i));
            } else {
                messages.add(ConversationMessage.assistant("Assistant response " + i));
            }
        }
        return messages;
    }

    private List<ConversationMessage> createLongMessages(int count) {
        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.system("System prompt"));
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longContent.append("This is a long message to consume token budget. ");
        }
        for (int i = 0; i < count - 1; i++) {
            messages.add(ConversationMessage.user(longContent.toString()));
        }
        return messages;
    }

    // --- Stub Implementations ---

    private static class StubLlmClient implements LlmClient {
        private LlmResponse response;

        void setResponse(LlmResponse response) {
            this.response = response;
        }

        @Override
        public LlmResponse chatStream(List<ConversationMessage> messages, List<Map<String, Object>> tools, Consumer<StreamEvent> eventConsumer) {
            return response != null ? response : new LlmResponse("Default", null, 10, 20);
        }

        @Override
        public void close() {
        }
    }

    private static class FailingLlmClient implements LlmClient {
        @Override
        public LlmResponse chatStream(List<ConversationMessage> messages, List<Map<String, Object>> tools, Consumer<StreamEvent> eventConsumer) {
            throw new RuntimeException("LLM error");
        }

        @Override
        public void close() {
        }
    }
}

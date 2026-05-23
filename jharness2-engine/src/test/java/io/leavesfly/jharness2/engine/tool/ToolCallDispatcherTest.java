package io.leavesfly.jharness2.engine.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.engine.model.ToolResultBlock;
import io.leavesfly.jharness2.engine.model.ToolUseBlock;
import io.leavesfly.jharness2.engine.permission.PermissionChecker;
import io.leavesfly.jharness2.engine.permission.PermissionMode;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import io.leavesfly.jharness2.engine.stream.ToolExecutionCompleted;
import io.leavesfly.jharness2.engine.stream.ToolExecutionStarted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallDispatcherTest {

    private ToolRegistry registry;
    private ToolCallDispatcher dispatcher;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        mapper = new ObjectMapper();
        dispatcher = new ToolCallDispatcher(registry, null, () -> Path.of("."));
    }

    @Test
    void shouldExecuteSingleTool() {
        registry.register(new EchoTool());

        JsonNode input = mapper.createObjectNode().put("text", "hello");
        ToolUseBlock toolUse = new ToolUseBlock("call_1", "echo", input);

        List<StreamEvent> events = new ArrayList<>();
        Consumer<StreamEvent> eventConsumer = events::add;

        List<ToolResultBlock> results = dispatcher.execute(List.of(toolUse), eventConsumer);

        assertEquals(1, results.size());
        assertFalse(results.get(0).isError());
        assertTrue(results.get(0).getContent().contains("hello"));

        // Verify events
        assertTrue(events.stream().anyMatch(e -> e instanceof ToolExecutionStarted));
        assertTrue(events.stream().anyMatch(e -> e instanceof ToolExecutionCompleted));
    }

    @Test
    void shouldExecuteMultipleToolsInParallel() {
        registry.register(new EchoTool());
        registry.register(new UpperTool());

        JsonNode input1 = mapper.createObjectNode().put("text", "hello");
        JsonNode input2 = mapper.createObjectNode().put("text", "world");

        ToolUseBlock toolUse1 = new ToolUseBlock("call_1", "echo", input1);
        ToolUseBlock toolUse2 = new ToolUseBlock("call_2", "upper", input2);

        List<ToolResultBlock> results = dispatcher.execute(List.of(toolUse1, toolUse2), event -> {});

        assertEquals(2, results.size());
        assertFalse(results.get(0).isError());
        assertFalse(results.get(1).isError());
    }

    @Test
    void shouldReturnErrorForUnknownTool() {
        JsonNode input = mapper.createObjectNode();
        ToolUseBlock toolUse = new ToolUseBlock("call_1", "unknown_tool", input);

        List<ToolResultBlock> results = dispatcher.execute(List.of(toolUse), event -> {});

        assertEquals(1, results.size());
        assertTrue(results.get(0).isError());
        assertTrue(results.get(0).getContent().contains("未知工具"));
    }

    @Test
    void shouldHandlePermissionDenial() {
        PermissionChecker checker = new PermissionChecker(PermissionMode.STRICT);
        dispatcher = new ToolCallDispatcher(registry, checker, () -> Path.of("."));

        registry.register(new FileWriteTool());

        JsonNode input = mapper.createObjectNode().put("file_path", "/etc/passwd").put("content", "test");
        ToolUseBlock toolUse = new ToolUseBlock("call_1", "file_write", input);

        List<ToolResultBlock> results = dispatcher.execute(List.of(toolUse), event -> {});

        assertEquals(1, results.size());
        assertTrue(results.get(0).isError());
        assertTrue(results.get(0).getContent().contains("权限拒绝"));
    }

    @Test
    void shouldSetPermissionChecker() {
        PermissionChecker checker = new PermissionChecker(PermissionMode.PERMISSIVE);
        dispatcher.setPermissionChecker(checker);

        registry.register(new EchoTool());
        JsonNode input = mapper.createObjectNode().put("text", "test");
        ToolUseBlock toolUse = new ToolUseBlock("call_1", "echo", input);

        List<ToolResultBlock> results = dispatcher.execute(List.of(toolUse), event -> {});

        assertEquals(1, results.size());
        assertFalse(results.get(0).isError());
    }

    @Test
    void shouldHandleToolException() {
        registry.register(new FailingTool());

        JsonNode input = mapper.createObjectNode();
        ToolUseBlock toolUse = new ToolUseBlock("call_1", "failing", input);

        List<ToolResultBlock> results = dispatcher.execute(List.of(toolUse), event -> {});

        assertEquals(1, results.size());
        assertTrue(results.get(0).isError());
    }

    @Test
    void shouldEmitStreamEvents() {
        registry.register(new EchoTool());

        JsonNode input = mapper.createObjectNode().put("text", "test");
        ToolUseBlock toolUse = new ToolUseBlock("call_1", "echo", input);

        AtomicBoolean startedReceived = new AtomicBoolean(false);
        AtomicBoolean completedReceived = new AtomicBoolean(false);

        Consumer<StreamEvent> eventConsumer = event -> {
            if (event instanceof ToolExecutionStarted) {
                startedReceived.set(true);
            } else if (event instanceof ToolExecutionCompleted) {
                completedReceived.set(true);
            }
        };

        dispatcher.execute(List.of(toolUse), eventConsumer);

        assertTrue(startedReceived.get(), "Should receive ToolExecutionStarted event");
        assertTrue(completedReceived.get(), "Should receive ToolExecutionCompleted event");
    }

    // --- Stub Tool Implementations ---

    private static class EchoTool extends BaseTool<Map<String, Object>> {
        @Override
        public String getName() {
            return "echo";
        }

        @Override
        public String getDescription() {
            return "Echo back the input text";
        }

        @Override
        public Class<Map<String, Object>> getInputClass() {
            @SuppressWarnings("unchecked")
            Class<Map<String, Object>> clazz = (Class<Map<String, Object>>) (Class<?>) Map.class;
            return clazz;
        }

        @Override
        public CompletableFuture<ToolResult> execute(Map<String, Object> input, ToolExecutionContext context) {
            String text = (String) input.getOrDefault("text", "");
            return CompletableFuture.completedFuture(ToolResult.success("Echo: " + text));
        }
    }

    private static class UpperTool extends BaseTool<Map<String, Object>> {
        @Override
        public String getName() {
            return "upper";
        }

        @Override
        public String getDescription() {
            return "Convert text to uppercase";
        }

        @Override
        public Class<Map<String, Object>> getInputClass() {
            @SuppressWarnings("unchecked")
            Class<Map<String, Object>> clazz = (Class<Map<String, Object>>) (Class<?>) Map.class;
            return clazz;
        }

        @Override
        public CompletableFuture<ToolResult> execute(Map<String, Object> input, ToolExecutionContext context) {
            String text = (String) input.getOrDefault("text", "");
            return CompletableFuture.completedFuture(ToolResult.success(text.toUpperCase()));
        }
    }

    private static class FileWriteTool extends BaseTool<Map<String, Object>> {
        @Override
        public String getName() {
            return "file_write";
        }

        @Override
        public String getDescription() {
            return "Write to a file";
        }

        @Override
        public Class<Map<String, Object>> getInputClass() {
            @SuppressWarnings("unchecked")
            Class<Map<String, Object>> clazz = (Class<Map<String, Object>>) (Class<?>) Map.class;
            return clazz;
        }

        @Override
        public boolean isReadOnly(Map<String, Object> input) {
            return false;
        }

        @Override
        public CompletableFuture<ToolResult> execute(Map<String, Object> input, ToolExecutionContext context) {
            return CompletableFuture.completedFuture(ToolResult.success("Written"));
        }
    }

    private static class FailingTool extends BaseTool<Map<String, Object>> {
        @Override
        public String getName() {
            return "failing";
        }

        @Override
        public String getDescription() {
            return "A tool that always fails";
        }

        @Override
        public Class<Map<String, Object>> getInputClass() {
            @SuppressWarnings("unchecked")
            Class<Map<String, Object>> clazz = (Class<Map<String, Object>>) (Class<?>) Map.class;
            return clazz;
        }

        @Override
        public CompletableFuture<ToolResult> execute(Map<String, Object> input, ToolExecutionContext context) {
            return CompletableFuture.completedFuture(ToolResult.error("Intentional failure"));
        }
    }
}

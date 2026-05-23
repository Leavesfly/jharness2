package io.leavesfly.jharness2.engine.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    void shouldRegisterTool() {
        StubTool tool = new StubTool("test_tool", "A test tool");
        registry.register(tool);

        assertTrue(registry.has("test_tool"));
        assertEquals(1, registry.size());
        assertSame(tool, registry.get("test_tool"));
    }

    @Test
    void shouldReturnNullForUnregisteredTool() {
        assertNull(registry.get("nonexistent"));
        assertFalse(registry.has("nonexistent"));
    }

    @Test
    void shouldSkipDuplicateRegistration() {
        StubTool tool1 = new StubTool("duplicate", "First tool");
        StubTool tool2 = new StubTool("duplicate", "Second tool");

        registry.register(tool1);
        registry.register(tool2);

        assertEquals(1, registry.size());
        assertSame(tool1, registry.get("duplicate"), "Should keep first registered tool");
    }

    @Test
    void shouldUnregisterTool() {
        StubTool tool = new StubTool("removable", "Removable tool");
        registry.register(tool);

        assertTrue(registry.unregister("removable"));
        assertFalse(registry.has("removable"));
        assertEquals(0, registry.size());
    }

    @Test
    void shouldReturnFalseWhenUnregisteringNonexistent() {
        assertFalse(registry.unregister("nonexistent"));
    }

    @Test
    void shouldGetAllToolNames() {
        registry.register(new StubTool("tool1", "Tool 1"));
        registry.register(new StubTool("tool2", "Tool 2"));
        registry.register(new StubTool("tool3", "Tool 3"));

        Set<String> names = registry.getToolNames();
        assertEquals(3, names.size());
        assertTrue(names.contains("tool1"));
        assertTrue(names.contains("tool2"));
        assertTrue(names.contains("tool3"));
    }

    @Test
    void shouldGenerateApiSchemas() {
        registry.register(new StubTool("echo", "Echo tool"));
        registry.register(new StubTool("calc", "Calculator tool"));

        var schemas = registry.toApiSchemas();

        assertEquals(2, schemas.size());
        assertTrue(schemas.stream().anyMatch(s -> "echo".equals(((Map<?, ?>) s.get("function")).get("name"))));
        assertTrue(schemas.stream().anyMatch(s -> "calc".equals(((Map<?, ?>) s.get("function")).get("name"))));
    }

    @Test
    void shouldHandleNullTool() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }

    @Test
    void shouldReturnEmptySchemaListWhenNoTools() {
        var schemas = registry.toApiSchemas();
        assertTrue(schemas.isEmpty());
    }

    @Test
    void shouldGetAllTools() {
        StubTool tool1 = new StubTool("t1", "Tool 1");
        StubTool tool2 = new StubTool("t2", "Tool 2");

        registry.register(tool1);
        registry.register(tool2);

        var tools = registry.getAllTools();
        assertEquals(2, tools.size());
        assertTrue(tools.contains(tool1));
        assertTrue(tools.contains(tool2));
    }

    // --- Stub Implementation ---

    private static class StubTool extends BaseTool<Map<String, Object>> {
        private final String name;
        private final String description;

        StubTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Class<Map<String, Object>> getInputClass() {
            @SuppressWarnings("unchecked")
            Class<Map<String, Object>> clazz = (Class<Map<String, Object>>) (Class<?>) Map.class;
            return clazz;
        }

        @Override
        public CompletableFuture<ToolResult> execute(Map<String, Object> input, ToolExecutionContext context) {
            return CompletableFuture.completedFuture(ToolResult.success("Executed: " + name));
        }
    }
}

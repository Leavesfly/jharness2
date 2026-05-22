package io.leavesfly.jharness2.engine.mcp;

import java.util.Map;

public class McpTool {
    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;

    public McpTool(String name, String description, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getInputSchema() { return inputSchema; }
}

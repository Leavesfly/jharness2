package io.leavesfly.jharness2.core.engine.tool;

import java.util.Map;

public class ToolDefinition {
    private final String name;
    private final String description;
    private final Map<String, Object> parameters;

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getParameters() { return parameters; }

    public Map<String, Object> toApiSchema() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters != null ? parameters : Map.of("type", "object", "properties", Map.of())
                )
        );
    }
}

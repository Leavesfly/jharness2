package io.leavesfly.jharness2.core.engine.stream;

public class ToolExecutionStarted extends StreamEvent {
    private final String toolName;
    private final String toolId;
    private final String input;

    public ToolExecutionStarted(String toolName, String toolId, String input) {
        this.toolName = toolName;
        this.toolId = toolId;
        this.input = input;
    }

    @Override
    public String getEventType() {
        return "tool_execution_started";
    }

    public String getToolName() { return toolName; }
    public String getToolId() { return toolId; }
    public String getInput() { return input; }
}

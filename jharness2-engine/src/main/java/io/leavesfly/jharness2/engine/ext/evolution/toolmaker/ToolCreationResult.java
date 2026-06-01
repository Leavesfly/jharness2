package io.leavesfly.jharness2.engine.ext.evolution.toolmaker;

import io.leavesfly.jharness2.engine.tool.BaseTool;

/**
 * 工具创建结果。
 */
public class ToolCreationResult {

    private final boolean success;
    private final String message;
    private final BaseTool<?> tool;
    private final String sourceCode;

    private ToolCreationResult(boolean success, String message, BaseTool<?> tool, String sourceCode) {
        this.success = success;
        this.message = message;
        this.tool = tool;
        this.sourceCode = sourceCode;
    }

    public static ToolCreationResult success(BaseTool<?> tool, String sourceCode) {
        return new ToolCreationResult(true, "Tool created successfully: " + tool.getName(), tool, sourceCode);
    }

    public static ToolCreationResult failure(String message) {
        return new ToolCreationResult(false, message, null, null);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public BaseTool<?> getTool() { return tool; }
    public String getSourceCode() { return sourceCode; }

    @Override
    public String toString() {
        return "ToolCreationResult{success=" + success + ", message='" + message + "'}";
    }
}

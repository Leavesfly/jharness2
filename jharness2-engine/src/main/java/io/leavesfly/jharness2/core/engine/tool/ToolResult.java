package io.leavesfly.jharness2.core.engine.tool;

/**
 * 工具执行结果。
 */
public class ToolResult {
    private final String output;
    private final boolean error;

    public ToolResult(String output, boolean error) {
        this.output = output;
        this.error = error;
    }

    public static ToolResult success(String output) {
        return new ToolResult(output, false);
    }

    public static ToolResult error(String message) {
        return new ToolResult(message, true);
    }

    public String getOutput() { return output; }
    public boolean isError() { return error; }

    @Override
    public String toString() {
        String preview = output != null && output.length() > 100 ? output.substring(0, 100) + "..." : output;
        return "ToolResult{output='" + preview + "', error=" + error + "}";
    }
}

package io.leavesfly.jharness2.engine.message;

/**
 * 工具执行结果内容块 —— 表示工具执行后的返回值。
 */
public class ToolResultBlock {
    private final String toolCallId;
    private final String content;
    private final boolean error;

    public ToolResultBlock(String toolCallId, String content, boolean error) {
        this.toolCallId = toolCallId;
        this.content = content;
        this.error = error;
    }

    public static ToolResultBlock success(String toolCallId, String content) {
        return new ToolResultBlock(toolCallId, content, false);
    }

    public static ToolResultBlock error(String toolCallId, String message) {
        return new ToolResultBlock(toolCallId, message, true);
    }

    public String getType() { return "tool_result"; }

    public String getToolCallId() { return toolCallId; }
    public String getContent() { return content; }
    public boolean isError() { return error; }

    @Override
    public String toString() {
        return "ToolResultBlock{toolCallId='" + toolCallId + "', error=" + error + "}";
    }
}

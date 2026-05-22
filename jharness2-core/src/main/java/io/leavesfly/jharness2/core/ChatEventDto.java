package io.leavesfly.jharness2.core;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一的聊天事件 DTO，从 core 层输出到 web 层用于 SSE 推送。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatEventDto {

    private String type;
    private String content;
    private String toolName;
    private String toolId;
    private String toolInput;
    private String toolResult;
    private Boolean toolError;
    private String error;
    private Boolean done;
    private Long inputTokens;
    private Long outputTokens;
    private Double costUsd;

    public static ChatEventDto text(String content) {
        ChatEventDto dto = new ChatEventDto();
        dto.type = "text";
        dto.content = content;
        return dto;
    }

    public static ChatEventDto toolStart(String toolName, String toolId) {
        ChatEventDto dto = new ChatEventDto();
        dto.type = "tool_start";
        dto.toolName = toolName;
        dto.toolId = toolId;
        return dto;
    }

    public static ChatEventDto toolEnd(String toolName, String result, boolean isError) {
        ChatEventDto dto = new ChatEventDto();
        dto.type = "tool_end";
        dto.toolName = toolName;
        dto.toolResult = result;
        dto.toolError = isError ? true : null;
        return dto;
    }

    public static ChatEventDto done() {
        ChatEventDto dto = new ChatEventDto();
        dto.type = "done";
        dto.done = true;
        return dto;
    }

    public static ChatEventDto error(String message) {
        ChatEventDto dto = new ChatEventDto();
        dto.type = "error";
        dto.error = message;
        return dto;
    }

    public static ChatEventDto usage(long inputTokens, long outputTokens, double costUsd) {
        ChatEventDto dto = new ChatEventDto();
        dto.type = "usage";
        dto.inputTokens = inputTokens;
        dto.outputTokens = outputTokens;
        dto.costUsd = costUsd;
        return dto;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }
    public String getToolInput() { return toolInput; }
    public void setToolInput(String toolInput) { this.toolInput = toolInput; }
    public String getToolResult() { return toolResult; }
    public void setToolResult(String toolResult) { this.toolResult = toolResult; }
    public Boolean getToolError() { return toolError; }
    public void setToolError(Boolean toolError) { this.toolError = toolError; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Boolean getDone() { return done; }
    public void setDone(Boolean done) { this.done = done; }
    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }
    public Double getCostUsd() { return costUsd; }
    public void setCostUsd(Double costUsd) { this.costUsd = costUsd; }
}

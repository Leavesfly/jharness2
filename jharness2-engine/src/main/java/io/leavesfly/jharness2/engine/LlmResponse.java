package io.leavesfly.jharness2.engine;

import java.util.List;

/**
 * LLM 单轮调用的完整响应结果。
 * chatStream 方法在 SSE 流结束后组装此对象返回给 QueryEngine。
 */
public class LlmResponse {

    private final String content;
    private final List<ConversationMessage.ToolCall> toolCalls;
    private final long promptTokens;
    private final long completionTokens;

    public LlmResponse(String content, List<ConversationMessage.ToolCall> toolCalls,
                       long promptTokens, long completionTokens) {
        this.content = content;
        this.toolCalls = toolCalls;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public String getContent() { return content; }

    public List<ConversationMessage.ToolCall> getToolCalls() { return toolCalls; }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public long getPromptTokens() { return promptTokens; }

    public long getCompletionTokens() { return completionTokens; }
}

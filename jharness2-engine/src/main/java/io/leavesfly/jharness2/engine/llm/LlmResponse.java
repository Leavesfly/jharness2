package io.leavesfly.jharness2.engine.llm;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.util.List;

/**
 * LLM 单轮调用的完整响应结果。
 * chatStream 方法在 SSE 流结束后组装此对象返回给 QueryEngine。
 */
public class LlmResponse {

    /**
     * LLM 回复终止原因。
     */
    public enum FinishReason {
        /** 正常结束（纯文本回复完毕） */
        STOP,
        /** 需要调用工具 */
        TOOL_CALLS,
        /** 达到 max_tokens 限制 */
        LENGTH,
        /** 内容过滤拒绝 */
        CONTENT_FILTER,
        /** 未知 */
        UNKNOWN
    }

    private final String content;
    private final List<ConversationMessage.ToolCall> toolCalls;
    private final long promptTokens;
    private final long completionTokens;
    private final FinishReason finishReason;
    private final String refusal;

    public LlmResponse(String content, List<ConversationMessage.ToolCall> toolCalls,
                       long promptTokens, long completionTokens) {
        this(content, toolCalls, promptTokens, completionTokens, null, null);
    }

    public LlmResponse(String content, List<ConversationMessage.ToolCall> toolCalls,
                       long promptTokens, long completionTokens,
                       FinishReason finishReason, String refusal) {
        this.content = content;
        this.toolCalls = toolCalls;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.finishReason = finishReason != null ? finishReason : inferFinishReason();
        this.refusal = refusal;
    }

    public String getContent() { return content; }

    public List<ConversationMessage.ToolCall> getToolCalls() { return toolCalls; }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public long getPromptTokens() { return promptTokens; }

    public long getCompletionTokens() { return completionTokens; }

    /** 获取终止原因 */
    public FinishReason getFinishReason() { return finishReason; }

    /** 获取拒绝原因（当 LLM 拒绝回答时非空） */
    public String getRefusal() { return refusal; }

    /** 是否被 LLM 拒绝 */
    public boolean isRefused() { return refusal != null && !refusal.isBlank(); }

    /** 是否因长度限制截断 */
    public boolean isTruncated() { return finishReason == FinishReason.LENGTH; }

    private FinishReason inferFinishReason() {
        if (hasToolCalls()) return FinishReason.TOOL_CALLS;
        return FinishReason.STOP;
    }

    /** 从 OpenAI finish_reason 字符串解析 */
    public static FinishReason parseFinishReason(String raw) {
        if (raw == null) return FinishReason.UNKNOWN;
        return switch (raw) {
            case "stop" -> FinishReason.STOP;
            case "tool_calls" -> FinishReason.TOOL_CALLS;
            case "length" -> FinishReason.LENGTH;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            default -> FinishReason.UNKNOWN;
        };
    }
}

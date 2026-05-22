package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.engine.stream.StreamEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM API 客户端接口。
 * chatStream 返回 LLM 本轮回复的结构化结果（包含 text 和 tool_calls），
 * 同时通过 eventConsumer 实时推送流式事件。
 */
public interface LlmClient {

    /**
     * 流式调用 LLM chat completion。
     *
     * @param messages     对话历史
     * @param tools        可用工具的 JSON Schema 列表
     * @param eventConsumer 流式事件回调（实时推送 delta）
     * @return 本轮 LLM 回复的完整结果（包含累积的 text 和 tool_calls）
     */
    LlmResponse chatStream(List<ConversationMessage> messages, List<Map<String, Object>> tools,
                            Consumer<StreamEvent> eventConsumer);

    void close();
}

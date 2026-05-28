package io.leavesfly.jharness2.engine.policy.pipeline;

import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.stream.StreamEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Middleware 链 — 用于将请求传递给下一个 Middleware 或最终的 LLM 调用。
 */
public interface MiddlewareChain {

    /**
     * 将请求传递给链中的下一个处理器。
     *
     * @param messages      对话历史（可能被前一个 middleware 修改）
     * @param tools         工具 schema 列表
     * @param eventConsumer 流式事件回调
     * @return LLM 响应（可能被后续 middleware 修改）
     */
    LlmResponse proceed(List<ConversationMessage> messages, List<Map<String, Object>> tools,
                         Consumer<StreamEvent> eventConsumer);
}

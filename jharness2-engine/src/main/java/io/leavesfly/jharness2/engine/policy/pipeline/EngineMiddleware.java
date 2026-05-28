package io.leavesfly.jharness2.engine.policy.pipeline;

import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.stream.StreamEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 引擎中间件 — 对 LLM 请求/响应做链式变换。
 * <p>
 * 与 Hook（事件驱动、fire-and-forget）不同，Middleware 可以：
 * <ul>
 *   <li>修改发送给 LLM 的消息（注入上下文、添加 prompt cache 标记等）</li>
 *   <li>修改 LLM 返回的响应（后处理、格式化等）</li>
 *   <li>拦截请求（如 rate limiting、缓存命中时跳过 LLM 调用）</li>
 *   <li>记录请求/响应（审计、日志）</li>
 * </ul>
 *
 * <p>执行顺序：注册顺序即为执行顺序（洋葱模型）。
 *
 * <pre>
 * Request → [Middleware A] → [Middleware B] → [LLM Call] → [Middleware B] → [Middleware A] → Response
 * </pre>
 */
@FunctionalInterface
public interface EngineMiddleware {

    /**
     * 拦截 LLM 调用。
     *
     * @param chain         链，调用 chain.proceed() 继续下一个 middleware 或最终的 LLM 调用
     * @param messages      当前对话历史
     * @param tools         工具 schema 列表
     * @param eventConsumer 流式事件回调
     * @return LLM 响应
     */
    LlmResponse intercept(MiddlewareChain chain, List<ConversationMessage> messages,
                           List<Map<String, Object>> tools, Consumer<StreamEvent> eventConsumer);
}

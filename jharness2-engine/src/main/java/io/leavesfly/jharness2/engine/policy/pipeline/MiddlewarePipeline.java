package io.leavesfly.jharness2.engine.policy.pipeline;

import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.message.ConversationMessage;

import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.stream.StreamEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Middleware 管道 — 管理和执行 Middleware 链。
 * <p>
 * 包装 LlmClient 调用，在调用前后依次执行注册的 Middleware。
 */
public class MiddlewarePipeline {

    private final List<EngineMiddleware> middlewares = new CopyOnWriteArrayList<>();
    private final LlmClient llmClient;

    public MiddlewarePipeline(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /** 添加 Middleware（按添加顺序执行） */
    public void addMiddleware(EngineMiddleware middleware) {
        middlewares.add(middleware);
    }

    /** 移除 Middleware */
    public void removeMiddleware(EngineMiddleware middleware) {
        middlewares.remove(middleware);
    }

    /**
     * 执行管道 — 依次经过所有 Middleware，最终调用 LlmClient。
     */
    public LlmResponse execute(List<ConversationMessage> messages, List<Map<String, Object>> tools,
                                Consumer<StreamEvent> eventConsumer) {
        if (middlewares.isEmpty()) {
            return llmClient.chatStream(messages, tools, eventConsumer);
        }

        // 构建链式调用（从最后一个 middleware 开始包装）
        MiddlewareChain chain = buildChain(new ArrayList<>(middlewares), messages, tools, eventConsumer);
        return chain.proceed(messages, tools, eventConsumer);
    }

    private MiddlewareChain buildChain(List<EngineMiddleware> mws,
                                        List<ConversationMessage> originalMessages,
                                        List<Map<String, Object>> originalTools,
                                        Consumer<StreamEvent> eventConsumer) {
        // 终端链 — 实际调用 LLM
        MiddlewareChain terminal = (msgs, tls, ec) -> llmClient.chatStream(msgs, tls, ec);

        // 从后向前包装
        MiddlewareChain current = terminal;
        for (int i = mws.size() - 1; i >= 0; i--) {
            EngineMiddleware mw = mws.get(i);
            MiddlewareChain next = current;
            current = (msgs, tls, ec) -> mw.intercept(next, msgs, tls, ec);
        }

        return current;
    }

    public int size() {
        return middlewares.size();
    }
}

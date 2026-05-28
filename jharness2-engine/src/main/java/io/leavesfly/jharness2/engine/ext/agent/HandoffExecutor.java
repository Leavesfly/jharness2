package io.leavesfly.jharness2.engine.ext.agent;

import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import io.leavesfly.jharness2.engine.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Handoff 执行器 — 创建并运行拥有完整 ReAct 能力的 Sub-Agent。
 * <p>
 * 与 {@link AgentOrchestrator} 的区别：
 * <ul>
 *   <li>AgentOrchestrator：仅做单轮 LLM 调用，Sub-Agent 无工具能力</li>
 *   <li>HandoffExecutor：Sub-Agent 是完整的 QueryEngine 实例，拥有独立的 ReAct 循环和工具集</li>
 * </ul>
 *
 * <p>设计参考 OpenAI Agents SDK 的 Handoff 和 AutoGen 的 Agent-to-Agent delegation。
 */
public class HandoffExecutor {

    private static final Logger logger = LoggerFactory.getLogger(HandoffExecutor.class);
    private static final int DEFAULT_SUB_AGENT_MAX_TURNS = 8;

    private final LlmClient llmClient;
    private final ToolRegistry sharedToolRegistry;
    private final ExecutorService executor;
    private final Map<String, Handoff> registeredHandoffs = new ConcurrentHashMap<>();

    public HandoffExecutor(LlmClient llmClient, ToolRegistry sharedToolRegistry) {
        this.llmClient = llmClient;
        this.sharedToolRegistry = sharedToolRegistry;
        this.executor = new ThreadPoolExecutor(
                2, Runtime.getRuntime().availableProcessors(),
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20),
                runnable -> {
                    Thread thread = new Thread(runnable, "jharness2-handoff-" + System.nanoTime());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 注册可用的 Handoff 定义。
     */
    public void register(Handoff handoff) {
        registeredHandoffs.put(handoff.getTargetAgentName(), handoff);
    }

    /**
     * 执行 Handoff — 创建 Sub-Agent 并运行 ReAct 循环。
     *
     * @param handoffName   目标 Agent 名称
     * @param prompt        交接给 Sub-Agent 的任务描述
     * @param parentMessages 主 Agent 的当前对话历史
     * @param eventConsumer 流式事件回调（可为 null）
     * @return Handoff 结果
     */
    public HandoffResult execute(String handoffName, String prompt,
                                  List<ConversationMessage> parentMessages,
                                  Consumer<StreamEvent> eventConsumer) {
        Handoff handoff = registeredHandoffs.get(handoffName);
        if (handoff == null) {
            return new HandoffResult(handoffName, "Unknown handoff target: " + handoffName,
                    false, 0, 0, List.of());
        }

        long startTime = System.currentTimeMillis();
        Consumer<StreamEvent> safeConsumer = eventConsumer != null ? eventConsumer : event -> {};

        try {
            // 构建 Sub-Agent 的系统提示
            AgentRole role = handoff.getRole();
            String subSystemPrompt = role.getSystemPrompt() != null
                    ? role.getSystemPrompt()
                    : "You are a specialized sub-agent named '" + handoffName + "'. Complete the assigned task.";

            // 创建 Sub-Agent 的 QueryEngine
            QueryEngine subEngine = new QueryEngine(
                    llmClient, sharedToolRegistry, subSystemPrompt, DEFAULT_SUB_AGENT_MAX_TURNS);

            // 应用输入过滤器，传递上下文
            if (handoff.getInputFilter() != null && parentMessages != null) {
                List<ConversationMessage> filtered = handoff.getInputFilter().filter(parentMessages);
                if (!filtered.isEmpty()) {
                    List<ConversationMessage> history = new ArrayList<>();
                    history.add(ConversationMessage.system(subSystemPrompt));
                    for (ConversationMessage msg : filtered) {
                        if (msg.getRole() != ConversationMessage.Role.SYSTEM) {
                            history.add(msg);
                        }
                    }
                    subEngine.loadMessages(history);
                }
            }

            // 执行 Sub-Agent
            CompletableFuture<Void> future = subEngine.submitMessage(prompt, safeConsumer);
            future.get(5, TimeUnit.MINUTES);

            // 提取结果
            List<ConversationMessage> agentMessages = subEngine.getMessages();
            String output = extractLastAssistantContent(agentMessages);
            int turnsUsed = countTurns(agentMessages);
            long duration = System.currentTimeMillis() - startTime;

            subEngine.close();

            logger.info("Handoff '{}' completed in {}ms, {} turns", handoffName, duration, turnsUsed);
            return new HandoffResult(handoffName, output, true, duration, turnsUsed, agentMessages);

        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Handoff '{}' timed out after {}ms", handoffName, duration);
            return new HandoffResult(handoffName, "Handoff timed out", false, duration, 0, List.of());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Handoff '{}' failed: {}", handoffName, e.getMessage(), e);
            return new HandoffResult(handoffName, "Handoff error: " + e.getMessage(), false, duration, 0, List.of());
        }
    }

    /**
     * 异步执行 Handoff。
     */
    public CompletableFuture<HandoffResult> executeAsync(String handoffName, String prompt,
                                                          List<ConversationMessage> parentMessages,
                                                          Consumer<StreamEvent> eventConsumer) {
        return CompletableFuture.supplyAsync(
                () -> execute(handoffName, prompt, parentMessages, eventConsumer), executor);
    }

    public Map<String, Handoff> getRegisteredHandoffs() {
        return Map.copyOf(registeredHandoffs);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private String extractLastAssistantContent(List<ConversationMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ConversationMessage msg = messages.get(i);
            if (msg.getRole() == ConversationMessage.Role.ASSISTANT && msg.getContent() != null) {
                return msg.getContent();
            }
        }
        return "";
    }

    private int countTurns(List<ConversationMessage> messages) {
        return (int) messages.stream()
                .filter(m -> m.getRole() == ConversationMessage.Role.ASSISTANT)
                .count();
    }
}

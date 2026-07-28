package io.leavesfly.jharness2.engine;

import io.leavesfly.jharness2.engine.llm.CostTracker;
import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.message.ToolResultBlock;
import io.leavesfly.jharness2.engine.message.ToolUseBlock;
import io.leavesfly.jharness2.engine.policy.access.Guardrail;
import io.leavesfly.jharness2.engine.policy.access.GuardrailExecutor;
import io.leavesfly.jharness2.engine.policy.access.GuardrailResult;
import io.leavesfly.jharness2.engine.policy.access.PermissionChecker;
import io.leavesfly.jharness2.engine.policy.context.CompactionStrategy;
import io.leavesfly.jharness2.engine.policy.context.ContextBudget;
import io.leavesfly.jharness2.engine.policy.context.TokenCounter;
import io.leavesfly.jharness2.engine.policy.observe.NoopTracer;
import io.leavesfly.jharness2.engine.policy.observe.Span;
import io.leavesfly.jharness2.engine.policy.observe.SpanStatus;
import io.leavesfly.jharness2.engine.policy.observe.Tracer;
import io.leavesfly.jharness2.engine.policy.pipeline.MiddlewarePipeline;
import io.leavesfly.jharness2.engine.ext.hook.HookEvent;
import io.leavesfly.jharness2.engine.ext.hook.HookExecutor;
import io.leavesfly.jharness2.engine.stream.*;
import io.leavesfly.jharness2.engine.tool.ToolCallDispatcher;
import io.leavesfly.jharness2.engine.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Agent 核心引擎 - 驱动 ReAct 循环（LLM 推理 ↔ 工具调用 ↔ 结果反馈）。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>管理 ReAct 循环（推理 → 工具调用 → 反馈 → 再推理）</li>
 *   <li>管理对话消息历史</li>
 *   <li>集成权限检查、消息压缩、Hook、会话持久化</li>
 * </ul>
 * 可选扩展子系统（Sub-Agent、Skill、MCP、Cron、后台任务等）通过 {@link EngineContext} 管理，
 * 使本类保持精简，专注于 ReAct 循环本身。
 */
public class QueryEngine implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(QueryEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- 核心依赖（构造时确定） ---
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolCallDispatcher toolCallDispatcher;
    private final String systemPrompt;
    private final int maxTurns;
    private final CostTracker costTracker = new CostTracker();
    private final List<ConversationMessage> messages = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    /** 同一引擎（session）同时只允许一个 ReAct 循环在跑，避免消息历史被并发写花 */
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile Path workingDirectory;
    /** Agent 循环执行器（由外部注入有界线程池，未注入时回退 commonPool 保持兼容） */
    private volatile Executor executor;

    // --- 可选核心子系统（与 ReAct 循环直接相关） ---
    private volatile PermissionChecker permissionChecker;
    private volatile CompactionStrategy compactionStrategy;
    private volatile HookExecutor hookExecutor;
    private volatile SessionPersister sessionPersister;
    private volatile Tracer tracer = NoopTracer.INSTANCE;
    private volatile TokenCounter tokenCounter;
    private volatile ContextBudget contextBudget;
    private volatile GuardrailExecutor guardrailExecutor;
    private volatile MiddlewarePipeline middlewarePipeline;

    // --- 扩展上下文（承载可选的非核心子系统） ---
    private volatile EngineContext engineContext;

    public QueryEngine(LlmClient llmClient, ToolRegistry toolRegistry,
                       String systemPrompt, int maxTurns) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
        this.maxTurns = maxTurns;
        this.toolCallDispatcher = new ToolCallDispatcher(toolRegistry, null, () -> getWorkingDirectory());
        this.messages.add(ConversationMessage.system(systemPrompt));
    }

    public CompletableFuture<Void> submitMessage(String prompt, Consumer<StreamEvent> eventConsumer) {
        // 同 session 互斥：并发的第二条消息直接快速失败，防止两个 ReAct 循环交错写 messages
        if (!busy.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new EngineBusyException(
                    "Engine is busy processing another message for this session"));
        }
        // 仅在成功获得 busy 后重置取消标志，避免误清除上一请求的取消状态
        cancelled.set(false);

        try {
            return runReactLoop(prompt, eventConsumer);
        } catch (RuntimeException ex) {
            // 线程池饱和拒绝等同步异常：必须释放 busy，否则引擎永久卡在忙碌态
            busy.set(false);
            return CompletableFuture.failedFuture(ex);
        }
    }

    private CompletableFuture<Void> runReactLoop(String prompt, Consumer<StreamEvent> eventConsumer) {
        return CompletableFuture.runAsync(() -> {
            Span sessionSpan = tracer.startSpan("submit-message", Map.of("prompt_length", prompt.length()));
            try {
                // Input Guardrail 检查
                if (guardrailExecutor != null) {
                    GuardrailResult inputCheck = guardrailExecutor.execute(
                            Guardrail.Phase.INPUT, new ArrayList<>(messages), prompt);
                    if (inputCheck.tripwire()) {
                        sessionSpan.setAttribute("guardrail_tripwire", inputCheck.reason());
                        sessionSpan.setStatus(SpanStatus.ERROR);
                        eventConsumer.accept(new AssistantTurnComplete(0));
                        return;
                    }
                }

                fireHook(HookEvent.USER_PROMPT_SUBMIT, Map.of("prompt", prompt));
                messages.add(ConversationMessage.user(prompt));
                compactIfNeeded();

                int turns = 0;
                while (turns < maxTurns && !cancelled.get()) {
                    turns++;

                    Span turnSpan = sessionSpan.startChild("react-turn-" + turns,
                            Map.of("turn", turns, "message_count", messages.size()));
                    try {
                        // LLM 调用（经由 Middleware 管道）
                        Span llmSpan = turnSpan.startChild("llm-call");
                        LlmResponse response;
                        try {
                            Consumer<StreamEvent> llmEventConsumer = event -> {
                                eventConsumer.accept(event);
                                if (event instanceof UsageReport report) {
                                    costTracker.addUsage(report.getInputTokens(), report.getOutputTokens());
                                }
                            };
                            if (middlewarePipeline != null) {
                                response = middlewarePipeline.execute(
                                        new ArrayList<>(messages), toolRegistry.toApiSchemas(), llmEventConsumer);
                            } else {
                                response = llmClient.chatStream(
                                        new ArrayList<>(messages), toolRegistry.toApiSchemas(), llmEventConsumer);
                            }
                            llmSpan.setAttribute("input_tokens", response.getPromptTokens());
                            llmSpan.setAttribute("output_tokens", response.getCompletionTokens());
                            llmSpan.setAttribute("has_tool_calls", response.hasToolCalls());
                            llmSpan.setAttribute("finish_reason", response.getFinishReason().name());
                            llmSpan.setStatus(SpanStatus.OK);
                        } catch (Exception e) {
                            llmSpan.setError(e);
                            throw e;
                        } finally {
                            llmSpan.close();
                        }

                        if (cancelled.get()) {
                            turnSpan.setAttribute("cancelled", true);
                            break;
                        }

                        List<ConversationMessage.ToolCall> toolCalls = response.getToolCalls();
                        if (!response.hasToolCalls()) {
                            // Output Guardrail 检查
                            String outputContent = response.getContent();
                            if (guardrailExecutor != null && outputContent != null) {
                                GuardrailResult outputCheck = guardrailExecutor.execute(
                                        Guardrail.Phase.OUTPUT, new ArrayList<>(messages), outputContent);
                                if (outputCheck.tripwire()) {
                                    turnSpan.setAttribute("guardrail_output_tripwire", outputCheck.reason());
                                    turnSpan.setStatus(SpanStatus.ERROR);
                                    eventConsumer.accept(new AssistantTurnComplete(turns));
                                    break;
                                }
                                if (outputCheck.correctedContent() != null) {
                                    outputContent = outputCheck.correctedContent();
                                }
                            }
                            messages.add(ConversationMessage.assistant(outputContent));
                            eventConsumer.accept(new AssistantTurnComplete(turns));
                            turnSpan.setAttribute("finish_reason", "no_tool_calls");
                            break;
                        }

                        messages.add(ConversationMessage.assistantWithToolCalls(response.getContent(), toolCalls));

                        // 工具调用
                        List<ToolUseBlock> toolUses = toolCalls.stream()
                                .map(tc -> new ToolUseBlock(tc.getId(), tc.getFunction().getName(),
                                        parseArgs(tc.getFunction().getArguments())))
                                .toList();

                        for (ToolUseBlock toolUse : toolUses) {
                            fireHook(HookEvent.PRE_TOOL_USE, Map.of("toolName", toolUse.getName()));
                        }

                        Span toolsSpan = turnSpan.startChild("tool-execution",
                                Map.of("tool_count", toolUses.size(),
                                        "tool_names", toolUses.stream().map(ToolUseBlock::getName).toList().toString()));
                        List<ToolResultBlock> results;
                        try {
                            results = toolCallDispatcher.execute(toolUses, eventConsumer);
                            long errorCount = results.stream().filter(ToolResultBlock::isError).count();
                            toolsSpan.setAttribute("error_count", errorCount);
                            toolsSpan.setStatus(errorCount > 0 ? SpanStatus.ERROR : SpanStatus.OK);
                        } catch (Exception e) {
                            toolsSpan.setError(e);
                            throw e;
                        } finally {
                            toolsSpan.close();
                        }

                        for (int i = 0; i < results.size(); i++) {
                            ToolResultBlock result = results.get(i);
                            ToolUseBlock toolUse = toolUses.get(i);
                            fireHook(HookEvent.POST_TOOL_USE,
                                    Map.of("toolName", toolUse.getName(), "result", result.getContent(), "isError", result.isError()));
                            messages.add(ConversationMessage.toolResult(result.getToolCallId(), toolUse.getName(), result.getContent()));
                        }

                        persistIfNeeded();
                        turnSpan.setStatus(SpanStatus.OK);
                    } catch (Exception e) {
                        turnSpan.setError(e);
                        throw e;
                    } finally {
                        turnSpan.close();
                    }
                }

                persistIfNeeded();
                sessionSpan.setAttribute("total_turns", turns);
                sessionSpan.setAttribute("total_input_tokens", costTracker.getInputTokens());
                sessionSpan.setAttribute("total_output_tokens", costTracker.getOutputTokens());
                sessionSpan.setStatus(SpanStatus.OK);
            } catch (Exception e) {
                sessionSpan.setError(e);
                throw e;
            } finally {
                sessionSpan.close();
            }
        }, executorOrDefault()).whenComplete((v, e) -> busy.set(false));
    }

    private Executor executorOrDefault() {
        Executor e = this.executor;
        return e != null ? e : ForkJoinPool.commonPool();
    }

    /**
     * 当前引擎是否正在处理消息。
     */
    public boolean isBusy() {
        return busy.get();
    }

    private com.fasterxml.jackson.databind.JsonNode parseArgs(String arguments) {
        try {
            return MAPPER.readTree(arguments != null ? arguments : "{}");
        } catch (Exception e) {
            logger.warn("Failed to parse tool arguments: {}", e.getMessage());
            return MAPPER.createObjectNode();
        }
    }

    private void compactIfNeeded() {
        CompactionStrategy strategy = this.compactionStrategy;
        if (strategy == null) return;

        boolean shouldCompact;
        // 优先使用 token 精确计数判断
        if (tokenCounter != null && contextBudget != null) {
            int currentTokens = tokenCounter.countMessages(new ArrayList<>(messages));
            shouldCompact = contextBudget.needsCompaction(currentTokens);
        } else {
            // 回退到 CompactionStrategy 自身的判断逻辑（通常基于消息数量）
            shouldCompact = strategy.needsCompaction(messages);
        }

        if (shouldCompact) {
            List<ConversationMessage> compacted = strategy.compact(new ArrayList<>(messages), llmClient);
            messages.clear();
            messages.addAll(compacted);
            logger.info("Messages compacted: now {} messages", messages.size());
        }
    }

    private void fireHook(HookEvent event, Map<String, Object> payload) {
        HookExecutor executor = this.hookExecutor;
        if (executor != null) {
            try {
                executor.fire(event, payload).join();
            } catch (Exception e) {
                logger.debug("Hook fire failed for {}: {}", event, e.getMessage());
            }
        }
    }

    private void persistIfNeeded() {
        SessionPersister persister = this.sessionPersister;
        if (persister != null) {
            try {
                persister.persist(new ArrayList<>(messages));
            } catch (Exception e) {
                // 持久化失败意味着宕机/驱逐时会丢数据，必须可观测
                logger.warn("Session persist failed (messages may be lost on restart): {}", e.getMessage());
            }
        }
    }

    // --- 生命周期 ---

    public void cancel() {
        cancelled.set(true);
        fireHook(HookEvent.STOP, Map.of());
    }

    @Override
    public void close() {
        cancelled.set(true);
        fireHook(HookEvent.SESSION_END, Map.of());
        if (engineContext != null) engineContext.close();
        if (llmClient != null) llmClient.close();
    }

    // --- 消息管理 ---

    public void loadMessages(List<ConversationMessage> history) {
        List<ConversationMessage> repaired = repairDanglingToolCalls(history);
        messages.clear();
        messages.addAll(repaired);
    }

    /**
     * 修复悬空的 tool_calls：中途取消/异常/强杀可能落库“有 tool_calls 但无 tool_result”的半成品历史，
     * 恢复后直接调 LLM 会被 OpenAI 兼容 API 拒绝（400），导致会话永久损坏。
     * 这里为缺失的 toolCallId 补合成 tool_result，标注执行被中断。
     */
    static List<ConversationMessage> repairDanglingToolCalls(List<ConversationMessage> history) {
        List<ConversationMessage> repaired = new ArrayList<>(history.size());
        int synthesized = 0;
        for (int i = 0; i < history.size(); i++) {
            ConversationMessage msg = history.get(i);
            repaired.add(msg);
            if (msg.getRole() != ConversationMessage.Role.ASSISTANT
                    || msg.getToolCalls() == null || msg.getToolCalls().isEmpty()) {
                continue;
            }
            // 收集紧随其后的 TOOL 消息已覆盖的 toolCallId
            Set<String> resolved = new HashSet<>();
            int j = i + 1;
            while (j < history.size() && history.get(j).getRole() == ConversationMessage.Role.TOOL) {
                resolved.add(history.get(j).getToolCallId());
                repaired.add(history.get(j));
                j++;
            }
            // 为缺失的 toolCallId 补合成结果，保证每个 tool_call 都有对应的 tool 消息
            for (ConversationMessage.ToolCall call : msg.getToolCalls()) {
                if (!resolved.contains(call.getId())) {
                    String toolName = call.getFunction() != null ? call.getFunction().getName() : "unknown";
                    repaired.add(ConversationMessage.toolResult(call.getId(), toolName,
                            "[Tool execution was interrupted before completion (engine restart/cancel)]"));
                    synthesized++;
                }
            }
            i = j - 1;
        }
        if (synthesized > 0) {
            logger.info("Repaired conversation history: synthesized {} missing tool result(s) for dangling tool calls",
                    synthesized);
        }
        return repaired;
    }

    public List<ConversationMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    // --- Getter / Setter ---

    public CostTracker getCostTracker() {
        return costTracker;
    }

    public LlmClient getLlmClient() {
        return llmClient;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public ToolCallDispatcher getToolCallDispatcher() {
        return toolCallDispatcher;
    }

    public PermissionChecker getPermissionChecker() {
        return permissionChecker;
    }

    public void setPermissionChecker(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
        // 同步更新 ToolCallDispatcher 中的权限检查器
        this.toolCallDispatcher.setPermissionChecker(permissionChecker);
    }

    public HookExecutor getHookExecutor() {
        return hookExecutor;
    }

    public void setHookExecutor(HookExecutor hookExecutor) {
        this.hookExecutor = hookExecutor;
    }

    public CompactionStrategy getCompactionStrategy() {
        return compactionStrategy;
    }

    public void setCompactionStrategy(CompactionStrategy compactionStrategy) {
        this.compactionStrategy = compactionStrategy;
    }

    public SessionPersister getSessionPersister() {
        return sessionPersister;
    }

    public void setSessionPersister(SessionPersister persister) {
        this.sessionPersister = persister;
    }

    public EngineContext getEngineContext() {
        return engineContext;
    }

    public void setEngineContext(EngineContext engineContext) {
        this.engineContext = engineContext;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer != null ? tracer : NoopTracer.INSTANCE;
    }

    public TokenCounter getTokenCounter() {
        return tokenCounter;
    }

    public void setTokenCounter(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    public ContextBudget getContextBudget() {
        return contextBudget;
    }

    public void setContextBudget(ContextBudget contextBudget) {
        this.contextBudget = contextBudget;
    }

    public GuardrailExecutor getGuardrailExecutor() {
        return guardrailExecutor;
    }

    public void setGuardrailExecutor(GuardrailExecutor guardrailExecutor) {
        this.guardrailExecutor = guardrailExecutor;
    }

    public MiddlewarePipeline getMiddlewarePipeline() {
        return middlewarePipeline;
    }

    public void setMiddlewarePipeline(MiddlewarePipeline middlewarePipeline) {
        this.middlewarePipeline = middlewarePipeline;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setWorkingDirectory(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Path getWorkingDirectory() {
        return workingDirectory != null ? workingDirectory : Path.of(".");
    }
}

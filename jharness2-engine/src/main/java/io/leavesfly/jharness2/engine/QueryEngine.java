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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    private volatile Path workingDirectory;

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
        return CompletableFuture.runAsync(() -> {
            cancelled.set(false);

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
        });
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
                logger.debug("Session persist failed (ignored): {}", e.getMessage());
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
        messages.clear();
        messages.addAll(history);
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

    public void setWorkingDirectory(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Path getWorkingDirectory() {
        return workingDirectory != null ? workingDirectory : Path.of(".");
    }
}

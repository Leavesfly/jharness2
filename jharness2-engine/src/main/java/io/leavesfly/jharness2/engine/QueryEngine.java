package io.leavesfly.jharness2.engine;

import io.leavesfly.jharness2.engine.compaction.CompactionStrategy;
import io.leavesfly.jharness2.engine.hook.HookEvent;
import io.leavesfly.jharness2.engine.hook.HookExecutor;
import io.leavesfly.jharness2.engine.model.ToolResultBlock;
import io.leavesfly.jharness2.engine.model.ToolUseBlock;
import io.leavesfly.jharness2.engine.permission.PermissionChecker;
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

            fireHook(HookEvent.USER_PROMPT_SUBMIT, Map.of("prompt", prompt));
            messages.add(ConversationMessage.user(prompt));
            compactIfNeeded();

            int turns = 0;
            while (turns < maxTurns && !cancelled.get()) {
                turns++;

                LlmResponse response = llmClient.chatStream(
                        new ArrayList<>(messages), toolRegistry.toApiSchemas(), event -> {
                            eventConsumer.accept(event);
                            if (event instanceof UsageReport report) {
                                costTracker.addUsage(report.getInputTokens(), report.getOutputTokens());
                            }
                        });

                if (cancelled.get()) break;

                List<ConversationMessage.ToolCall> toolCalls = response.getToolCalls();
                if (!response.hasToolCalls()) {
                    messages.add(ConversationMessage.assistant(response.getContent()));
                    eventConsumer.accept(new AssistantTurnComplete(turns));
                    break;
                }

                messages.add(ConversationMessage.assistantWithToolCalls(response.getContent(), toolCalls));

                // 委托 ToolCallDispatcher 执行工具调用
                List<ToolUseBlock> toolUses = toolCalls.stream()
                        .map(tc -> new ToolUseBlock(tc.getId(), tc.getFunction().getName(),
                                parseArgs(tc.getFunction().getArguments())))
                        .toList();

                // 触发 Hook 并执行
                for (ToolUseBlock toolUse : toolUses) {
                    fireHook(HookEvent.PRE_TOOL_USE, Map.of("toolName", toolUse.getName()));
                }

                List<ToolResultBlock> results = toolCallDispatcher.execute(toolUses, eventConsumer);

                for (int i = 0; i < results.size(); i++) {
                    ToolResultBlock result = results.get(i);
                    ToolUseBlock toolUse = toolUses.get(i);
                    fireHook(HookEvent.POST_TOOL_USE,
                            Map.of("toolName", toolUse.getName(), "result", result.getContent(), "isError", result.isError()));
                    messages.add(ConversationMessage.toolResult(result.getToolCallId(), toolUse.getName(), result.getContent()));
                }

                persistIfNeeded();
            }

            persistIfNeeded();
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
        if (strategy != null && strategy.needsCompaction(messages)) {
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

    public void setWorkingDirectory(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Path getWorkingDirectory() {
        return workingDirectory != null ? workingDirectory : Path.of(".");
    }
}

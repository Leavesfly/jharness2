package io.leavesfly.jharness2.engine;

import io.leavesfly.jharness2.engine.agent.AgentOrchestrator;
import io.leavesfly.jharness2.engine.compaction.MessageCompactionService;
import io.leavesfly.jharness2.engine.cron.CronScheduler;
import io.leavesfly.jharness2.engine.heartbeat.HeartbeatService;
import io.leavesfly.jharness2.engine.hook.HookEvent;
import io.leavesfly.jharness2.engine.hook.HookExecutor;
import io.leavesfly.jharness2.engine.mcp.McpManager;
import io.leavesfly.jharness2.engine.permission.PermissionChecker;
import io.leavesfly.jharness2.engine.skill.SkillRegistry;

import io.leavesfly.jharness2.engine.stream.*;
import io.leavesfly.jharness2.engine.task.BackgroundTaskManager;
import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.engine.tool.ToolRegistry;
import io.leavesfly.jharness2.engine.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Agent 核心引擎 - 驱动 ReAct 循环（LLM 调用 ↔ 工具调用 ↔ 结果反馈）。
 * 集成权限系统、消息压缩、Sub-Agent、Skill、MCP、Hook、后台任务等子系统。
 */
public class QueryEngine {

    private static final Logger logger = LoggerFactory.getLogger(QueryEngine.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final String systemPrompt;
    private final int maxTurns;
    private final CostTracker costTracker = new CostTracker();
    private final List<ConversationMessage> messages = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Path workingDirectory;

    // 子系统（可选注入）
    private volatile PermissionChecker permissionChecker;
    private volatile MessageCompactionService compactionService;
    private volatile AgentOrchestrator agentOrchestrator;
    private volatile SkillRegistry skillRegistry;
    private volatile McpManager mcpManager;
    private volatile HookExecutor hookExecutor;
    private volatile BackgroundTaskManager backgroundTaskManager;
    private volatile HeartbeatService heartbeatService;
    private volatile CronScheduler cronScheduler;
    private volatile Consumer<List<ConversationMessage>> sessionPersister;

    public QueryEngine(LlmClient llmClient, ToolRegistry toolRegistry,
                       String systemPrompt, int maxTurns) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
        this.maxTurns = maxTurns;
        this.messages.add(ConversationMessage.system(systemPrompt));
    }

    public CompletableFuture<Void> submitMessage(String prompt, Consumer<StreamEvent> eventConsumer) {
        return CompletableFuture.runAsync(() -> {
            cancelled.set(false);

            // Hook: USER_PROMPT_SUBMIT
            fireHook(HookEvent.USER_PROMPT_SUBMIT, Map.of("prompt", prompt));

            messages.add(ConversationMessage.user(prompt));

            // 消息压缩：如果历史过长则压缩
            compactIfNeeded();

            int turns = 0;
            while (turns < maxTurns && !cancelled.get()) {
                turns++;

                // 调用 LLM，获取完整响应（含 text + tool_calls）
                LlmResponse response = llmClient.chatStream(
                        new ArrayList<>(messages), toolRegistry.toApiSchemas(), event -> {
                            eventConsumer.accept(event);
                            if (event instanceof UsageReport report) {
                                costTracker.addUsage(report.getInputTokens(), report.getOutputTokens());
                            }
                        });

                if (cancelled.get()) break;

                // 将 assistant 消息加入历史
                List<ConversationMessage.ToolCall> toolCalls = response.getToolCalls();
                if (response.hasToolCalls()) {
                    messages.add(ConversationMessage.assistantWithToolCalls(response.getContent(), toolCalls));
                } else {
                    messages.add(ConversationMessage.assistant(response.getContent()));
                    eventConsumer.accept(new AssistantTurnComplete(turns));
                    break;
                }

                // 执行工具调用（通过 BaseTool 体系）
                for (ConversationMessage.ToolCall toolCall : toolCalls) {
                    if (cancelled.get()) break;
                    String toolName = toolCall.getFunction().getName();
                    String toolArgs = toolCall.getFunction().getArguments();

                    // 权限检查
                    if (permissionChecker != null && !permissionChecker.isToolAllowed(toolName)) {
                        String denied = "Permission denied: tool '" + toolName + "' is not allowed";
                        eventConsumer.accept(new ToolExecutionCompleted(toolName, toolCall.getId(), denied, true));
                        messages.add(ConversationMessage.toolResult(toolCall.getId(), toolName, denied));
                        continue;
                    }

                    fireHook(HookEvent.PRE_TOOL_USE, Map.of("toolName", toolName, "toolArgs", toolArgs));
                    eventConsumer.accept(new ToolExecutionStarted(toolName, toolCall.getId(), toolArgs));

                    ToolResult toolResult = executeTool(toolName, toolArgs);

                    fireHook(HookEvent.POST_TOOL_USE,
                            Map.of("toolName", toolName, "result", toolResult.getOutput(), "isError", toolResult.isError()));
                    eventConsumer.accept(new ToolExecutionCompleted(toolName, toolCall.getId(), toolResult.getOutput(), toolResult.isError()));
                    messages.add(ConversationMessage.toolResult(toolCall.getId(), toolName, toolResult.getOutput()));
                }

                persistIfNeeded();
            }

            persistIfNeeded();
        });
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeTool(String toolName, String toolArgs) {
        BaseTool<Object> tool = (BaseTool<Object>) toolRegistry.get(toolName);
        if (tool == null) {
            return ToolResult.error("未知工具: " + toolName);
        }
        try {
            JsonNode argsNode = MAPPER.readTree(toolArgs != null ? toolArgs : "{}");
            Object input = MAPPER.treeToValue(argsNode, tool.getInputClass());

            // 权限检查（路径 + 命令级别）
            if (permissionChecker != null) {
                String filePath = argsNode.has("file_path") ? argsNode.get("file_path").asText() : null;
                String command = argsNode.has("command") ? argsNode.get("command").asText() : null;
                if (!permissionChecker.isAllowed(toolName, tool.isReadOnly(input), filePath, command)) {
                    return ToolResult.error("权限拒绝: 操作不被允许");
                }
            }

            Path cwd = workingDirectory != null ? workingDirectory : Path.of(".");
            ToolExecutionContext context = new ToolExecutionContext(cwd, permissionChecker);
            return tool.execute(input, context).join();
        } catch (Exception e) {
            logger.error("Tool execution failed: {}", toolName, e);
            return ToolResult.error("工具执行失败: " + e.getMessage());
        }
    }

    private void compactIfNeeded() {
        if (compactionService != null && compactionService.needsCompaction(messages)) {
            List<ConversationMessage> compacted = compactionService.compact(new ArrayList<>(messages), llmClient);
            messages.clear();
            messages.addAll(compacted);
            logger.info("Messages compacted: now {} messages", messages.size());
        }
    }

    private void fireHook(HookEvent event, Map<String, Object> payload) {
        if (hookExecutor != null) {
            try {
                hookExecutor.fire(event, payload).join();
            } catch (Exception e) {
                logger.debug("Hook fire failed for {}: {}", event, e.getMessage());
            }
        }
    }

    private void persistIfNeeded() {
        Consumer<List<ConversationMessage>> persister = this.sessionPersister;
        if (persister != null) {
            try {
                persister.accept(new ArrayList<>(messages));
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

    public void close() {
        cancelled.set(true);
        fireHook(HookEvent.SESSION_END, Map.of());
        if (mcpManager != null) mcpManager.close();
        if (agentOrchestrator != null) agentOrchestrator.shutdown();
        if (backgroundTaskManager != null) backgroundTaskManager.shutdown();
        if (heartbeatService != null) heartbeatService.stop();
        if (cronScheduler != null) cronScheduler.shutdown();
        llmClient.close();
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

    public PermissionChecker getPermissionChecker() {
        return permissionChecker;
    }

    public AgentOrchestrator getAgentOrchestrator() {
        return agentOrchestrator;
    }

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    public McpManager getMcpManager() {
        return mcpManager;
    }

    public HookExecutor getHookExecutor() {
        return hookExecutor;
    }

    public BackgroundTaskManager getBackgroundTaskManager() {
        return backgroundTaskManager;
    }

    public HeartbeatService getHeartbeatService() {
        return heartbeatService;
    }

    public CronScheduler getCronScheduler() {
        return cronScheduler;
    }

    public void setPermissionChecker(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    public void setCompactionService(MessageCompactionService compactionService) {
        this.compactionService = compactionService;
    }

    public void setAgentOrchestrator(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    public void setSkillRegistry(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public void setMcpManager(McpManager mcpManager) {
        this.mcpManager = mcpManager;
    }

    public void setHookExecutor(HookExecutor hookExecutor) {
        this.hookExecutor = hookExecutor;
    }

    public void setBackgroundTaskManager(BackgroundTaskManager backgroundTaskManager) {
        this.backgroundTaskManager = backgroundTaskManager;
    }

    public void setHeartbeatService(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    public void setCronScheduler(CronScheduler cronScheduler) {
        this.cronScheduler = cronScheduler;
    }

    public void setSessionPersister(Consumer<List<ConversationMessage>> persister) {
        this.sessionPersister = persister;
    }

    public void setWorkingDirectory(java.nio.file.Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public java.nio.file.Path getWorkingDirectory() {
        return workingDirectory;
    }
}

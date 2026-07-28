package io.leavesfly.jharness2.engine.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.engine.message.ToolResultBlock;
import io.leavesfly.jharness2.engine.message.ToolUseBlock;
import io.leavesfly.jharness2.engine.policy.access.PermissionChecker;
import io.leavesfly.jharness2.engine.policy.resilience.ErrorRecoveryStrategy;
import io.leavesfly.jharness2.engine.policy.resilience.RecoveryAction;
import io.leavesfly.jharness2.engine.policy.resilience.RecoveryDecision;
import io.leavesfly.jharness2.engine.policy.resilience.ToolExecutionPolicy;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import io.leavesfly.jharness2.engine.stream.ToolExecutionCompleted;
import io.leavesfly.jharness2.engine.stream.ToolExecutionStarted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 工具调用调度器：解析 LLM 返回的 tool_calls，执行工具，返回结果。
 * <p>
 * 单工具顺序执行；多工具并行执行（5 分钟超时）。
 * 执行前后推送 StreamEvent，集成权限检查。
 */
public final class ToolCallDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallDispatcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long PARALLEL_TIMEOUT_MINUTES = 5;

    private final ToolRegistry toolRegistry;
    private volatile PermissionChecker permissionChecker;
    private volatile ErrorRecoveryStrategy recoveryStrategy;
    private volatile ToolExecutionPolicy executionPolicy;
    /** 工具执行器（由外部注入有界线程池，未注入时回退 commonPool 保持兼容） */
    private volatile Executor toolExecutor;
    private final Supplier<Path> cwdSupplier;

    public ToolCallDispatcher(ToolRegistry toolRegistry, PermissionChecker permissionChecker, Supplier<Path> cwdSupplier) {
        this.toolRegistry = toolRegistry;
        this.permissionChecker = permissionChecker;
        this.cwdSupplier = cwdSupplier;
    }

    public void setPermissionChecker(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    public void setRecoveryStrategy(ErrorRecoveryStrategy recoveryStrategy) {
        this.recoveryStrategy = recoveryStrategy;
    }

    public void setExecutionPolicy(ToolExecutionPolicy executionPolicy) {
        this.executionPolicy = executionPolicy;
    }

    public void setToolExecutor(Executor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    private Executor toolExecutorOrDefault() {
        Executor e = this.toolExecutor;
        return e != null ? e : ForkJoinPool.commonPool();
    }

    /**
     * 执行一组工具调用，返回顺序对应的 ToolResultBlock 列表。
     */
    public List<ToolResultBlock> execute(List<ToolUseBlock> toolUses, Consumer<StreamEvent> eventConsumer) {
        if (toolUses.size() == 1) {
            return executeSingle(toolUses.get(0), eventConsumer);
        }
        return executeParallel(toolUses, eventConsumer);
    }

    private List<ToolResultBlock> executeSingle(ToolUseBlock toolUse, Consumer<StreamEvent> eventConsumer) {
        eventConsumer.accept(new ToolExecutionStarted(toolUse.getName(), toolUse.getId(), toolUse.getInput() != null ? toolUse.getInput().toString() : ""));

        ToolResult result = executeWithRecovery(toolUse);

        eventConsumer.accept(new ToolExecutionCompleted(toolUse.getName(), toolUse.getId(), result.getOutput(), result.isError()));
        return List.of(new ToolResultBlock(toolUse.getId(), result.getOutput(), result.isError()));
    }

    private ToolResult executeWithRecovery(ToolUseBlock toolUse) {
        int attempt = 0;
        while (true) {
            attempt++;
            ToolResult result;
            try {
                result = executeToolCall(toolUse).join();
            } catch (CancellationException ce) {
                result = ToolResult.error("工具执行被取消");
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                logger.error("Tool execution error: {}", toolUse.getName(), cause);
                result = ToolResult.error("工具执行异常: " + cause.getMessage());
            }

            // 成功则直接返回
            if (!result.isError()) {
                return result;
            }

            // 无恢复策略时直接返回错误
            if (recoveryStrategy == null) {
                return result;
            }

            // 执行恢复策略
            RecoveryDecision decision = recoveryStrategy.onToolError(toolUse.getName(), result.getOutput(), attempt);
            switch (decision.action()) {
                case RETRY -> {
                    logger.info("Recovery: retrying tool '{}' (attempt {})", toolUse.getName(), attempt + 1);
                    continue;
                }
                case SKIP -> {
                    logger.info("Recovery: skipping tool '{}': {}", toolUse.getName(), decision.message());
                    return ToolResult.success("[Skipped] " + decision.message());
                }
                case ABORT -> {
                    logger.warn("Recovery: aborting due to tool '{}' failure: {}", toolUse.getName(), decision.message());
                    return ToolResult.error("[Aborted] " + decision.message());
                }
                default -> {
                    // ESCALATE / FALLBACK: 返回错误让 LLM 决策
                    return result;
                }
            }
        }
    }

    private List<ToolResultBlock> executeParallel(List<ToolUseBlock> toolUses, Consumer<StreamEvent> eventConsumer) {
        List<CompletableFuture<ToolResult>> futures = new ArrayList<>();
        for (ToolUseBlock toolUse : toolUses) {
            eventConsumer.accept(new ToolExecutionStarted(toolUse.getName(), toolUse.getId(), toolUse.getInput() != null ? toolUse.getInput().toString() : ""));
            CompletableFuture<ToolResult> future = executeToolCall(toolUse).thenApply(result -> {
                eventConsumer.accept(new ToolExecutionCompleted(toolUse.getName(), toolUse.getId(), result.getOutput(), result.isError()));
                return result;
            });
            futures.add(future);
        }

        // 使用策略中最大的单工具超时作为并行超时上限
        long timeoutMinutes = PARALLEL_TIMEOUT_MINUTES;
        if (executionPolicy != null) {
            long maxToolTimeoutMs = toolUses.stream()
                    .mapToLong(t -> executionPolicy.getTimeout(t.getName()).toMillis())
                    .max()
                    .orElse(PARALLEL_TIMEOUT_MINUTES * 60 * 1000);
            timeoutMinutes = Math.max(1, (maxToolTimeoutMs / 60000) + 1);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            logger.error("Tool parallel execution timeout ({}min)", timeoutMinutes);
            futures.forEach(f -> f.cancel(true));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Tool parallel execution error", e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }

        return collectResults(toolUses, futures);
    }

    private List<ToolResultBlock> collectResults(List<ToolUseBlock> toolUses, List<CompletableFuture<ToolResult>> futures) {
        List<ToolResultBlock> results = new ArrayList<>();
        for (int i = 0; i < toolUses.size(); i++) {
            ToolUseBlock toolUse = toolUses.get(i);
            CompletableFuture<ToolResult> future = futures.get(i);
            ToolResult result;
            try {
                result = future.getNow(ToolResult.error("工具执行超时"));
            } catch (CancellationException ce) {
                result = ToolResult.error("工具执行被取消");
            } catch (Exception ex) {
                result = ToolResult.error("工具执行异常: " + ex.getMessage());
            }
            results.add(new ToolResultBlock(toolUse.getId(), result.getOutput(), result.isError()));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolResult> executeToolCall(ToolUseBlock toolUse) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BaseTool<Object> tool = (BaseTool<Object>) toolRegistry.get(toolUse.getName());
                if (tool == null) {
                    return ToolResult.error("未知工具: " + toolUse.getName());
                }

                Object input = MAPPER.treeToValue(toolUse.getInput(), tool.getInputClass());

                // 权限检查
                if (permissionChecker != null) {
                    String filePath = extractField(toolUse.getInput(), "file_path", "path");
                    String command = extractField(toolUse.getInput(), "command");
                    // 相对路径按引擎工作目录解析，否则会退化成按进程 cwd 判定，导致隔离规则失效
                    if (filePath != null && !filePath.isBlank()) {
                        filePath = resolveAgainstCwd(filePath);
                    }
                    if (!permissionChecker.isAllowed(tool.getName(), tool.isReadOnly(input), filePath, command)) {
                        return ToolResult.error("权限拒绝: 操作不被允许");
                    }
                }

                ToolExecutionContext context = new ToolExecutionContext(cwdSupplier.get(), permissionChecker);
                return tool.execute(input, context).join();
            } catch (Exception e) {
                logger.error("Tool execution failed: {}", toolUse.getName(), e);
                return ToolResult.error("工具执行失败: " + e.getMessage());
            }
        }, toolExecutorOrDefault());
    }

    private static String extractField(JsonNode input, String... fieldNames) {
        if (input == null) return null;
        for (String name : fieldNames) {
            if (input.has(name)) return input.get(name).asText();
        }
        return null;
    }

    private String resolveAgainstCwd(String filePath) {
        try {
            Path cwd = cwdSupplier.get();
            if (cwd == null) return filePath;
            return cwd.resolve(filePath).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return filePath;
        }
    }
}

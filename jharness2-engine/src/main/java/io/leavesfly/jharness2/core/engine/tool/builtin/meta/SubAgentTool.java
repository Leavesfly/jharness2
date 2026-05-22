package io.leavesfly.jharness2.core.engine.tool.builtin.meta;

import io.leavesfly.jharness2.core.engine.agent.AgentOrchestrator;
import io.leavesfly.jharness2.core.engine.agent.AgentResult;
import io.leavesfly.jharness2.core.engine.agent.AgentRole;
import io.leavesfly.jharness2.core.engine.agent.AgentTask;
import io.leavesfly.jharness2.core.engine.tool.BaseTool;
import io.leavesfly.jharness2.core.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.core.engine.tool.ToolResult;
import io.leavesfly.jharness2.core.engine.tool.input.SubAgentInput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Sub-Agent 工具 - LLM 通过此工具将复杂任务拆分并分派给多个子 Agent 并行/串行执行。
 * <p>
 * 每个子 Agent 拥有独立的对话上下文，执行完成后汇总结果返回主 Agent。
 */
public class SubAgentTool extends BaseTool<SubAgentInput> {

    private final AgentOrchestrator orchestrator;

    public SubAgentTool(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public String getName() { return "sub_agent"; }

    @Override
    public String getDescription() {
        return "将复杂任务拆分给多个子 Agent 执行。tasks 为任务列表（含 description 和 prompt），mode 为 parallel（并行）或 sequential（串行）。";
    }

    @Override
    public Class<SubAgentInput> getInputClass() { return SubAgentInput.class; }

    @Override
    public boolean isReadOnly(SubAgentInput input) { return true; }

    @Override
    public CompletableFuture<ToolResult> execute(SubAgentInput input, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            if (orchestrator == null) {
                return ToolResult.error("Sub-Agent 系统未启用");
            }
            if (input.getTasks() == null || input.getTasks().isEmpty()) {
                return ToolResult.error("tasks 不能为空");
            }

            List<AgentTask> tasks = input.getTasks().stream()
                    .map(t -> new AgentTask(
                            t.getDescription() != null ? t.getDescription() : "task",
                            t.getPrompt() != null ? t.getPrompt() : "",
                            AgentRole.WORKER))
                    .collect(Collectors.toList());

            String mode = input.getMode() != null ? input.getMode() : "parallel";
            List<AgentResult> results = "sequential".equals(mode)
                    ? orchestrator.executeSequential(tasks)
                    : orchestrator.executeParallel(tasks);

            StringBuilder output = new StringBuilder();
            output.append("Sub-Agent 执行完成 (").append(results.size()).append(" 个任务, mode=").append(mode).append("):\n\n");
            for (int i = 0; i < results.size(); i++) {
                AgentResult r = results.get(i);
                output.append("--- Task ").append(i + 1).append(" [").append(r.isSuccess() ? "✓" : "✗").append("] ---\n");
                output.append(r.getOutput()).append("\n\n");
            }
            return ToolResult.success(output.toString());
        });
    }
}

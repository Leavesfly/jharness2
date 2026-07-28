package io.leavesfly.jharness2.engine.tool.builtin.cron;

import io.leavesfly.jharness2.engine.ext.cron.CronJob;
import io.leavesfly.jharness2.engine.ext.cron.CronScheduler;
import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.engine.tool.ToolResult;
import io.leavesfly.jharness2.engine.tool.input.CronToolInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cron 定时任务工具 - 将 cron 调度能力暴露给 LLM。
 * <p>
 * 支持的操作：
 * <ul>
 *   <li><b>register</b> - 注册定时任务（需要 name、cron_expression、command）</li>
 *   <li><b>list</b> - 列出所有已注册的定时任务</li>
 *   <li><b>remove</b> - 移除指定任务（需要 job_id）</li>
 *   <li><b>pause</b> - 暂停指定任务（需要 job_id）</li>
 *   <li><b>resume</b> - 恢复指定任务（需要 job_id）</li>
 *   <li><b>trigger</b> - 手动触发指定任务（需要 job_id）</li>
 * </ul>
 */
public class CronTool extends BaseTool<CronToolInput> {

    private static final Logger logger = LoggerFactory.getLogger(CronTool.class);
    private static final int COMMAND_TIMEOUT_SECONDS = 300;

    private final CronScheduler cronScheduler;

    public CronTool(CronScheduler cronScheduler) {
        if (cronScheduler == null) {
            throw new IllegalArgumentException("cronScheduler must not be null");
        }
        this.cronScheduler = cronScheduler;
    }

    @Override
    public String getName() { return "cron"; }

    @Override
    public String getDescription() {
        return "管理 cron 定时任务。支持的 action: register(注册定时任务), list(列出所有任务), "
                + "remove(移除任务), pause(暂停任务), resume(恢复任务), trigger(手动触发任务)。"
                + "register 时需要提供 name(任务名称)、cron_expression(cron表达式，5段格式如 '*/5 * * * *')、command(要执行的shell命令)。"
                + "remove/pause/resume/trigger 时需要提供 job_id。";
    }

    @Override
    public Class<CronToolInput> getInputClass() { return CronToolInput.class; }

    @Override
    public boolean isReadOnly(CronToolInput input) {
        return "list".equals(input.getAction());
    }

    @Override
    public CompletableFuture<ToolResult> execute(CronToolInput input, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String action = input.getAction();
            if (action == null || action.isBlank()) {
                return ToolResult.error("action 不能为空，可选值: register, list, remove, pause, resume, trigger");
            }

            return switch (action.toLowerCase().trim()) {
                case "register" -> handleRegister(input, context);
                case "list" -> handleList();
                case "remove" -> handleRemove(input);
                case "pause" -> handlePause(input);
                case "resume" -> handleResume(input);
                case "trigger" -> handleTrigger(input, context);
                default -> ToolResult.error("未知的 action: " + action
                        + "，可选值: register, list, remove, pause, resume, trigger");
            };
        });
    }

    private ToolResult handleRegister(CronToolInput input, ToolExecutionContext context) {
        String name = input.getName();
        String cronExpr = input.getCron_expression();
        String command = input.getCommand();

        if (name == null || name.isBlank()) {
            return ToolResult.error("register 操作需要提供 name（任务名称）");
        }
        if (cronExpr == null || cronExpr.isBlank()) {
            return ToolResult.error("register 操作需要提供 cron_expression（cron 表达式）");
        }
        if (command == null || command.isBlank()) {
            return ToolResult.error("register 操作需要提供 command（要执行的 shell 命令）");
        }
        // 注册时即校验命令权限，阻断通过延迟执行绕过黑名单的路径
        if (context != null && context.getPermissionChecker() != null
                && !context.getPermissionChecker().isCommandAllowed(command)) {
            return ToolResult.error("权限拒绝: 该命令不允许注册为定时任务");
        }

        try {
            CronJob job = cronScheduler.register(name, cronExpr, ctx -> {
                executeShellCommand(command, context);
            });

            // 确保调度器已启动
            if (!cronScheduler.isRunning()) {
                cronScheduler.start();
            }

            StringBuilder result = new StringBuilder();
            result.append("定时任务注册成功\n");
            result.append("  job_id: ").append(job.getJobId()).append("\n");
            result.append("  name: ").append(job.getName()).append("\n");
            result.append("  cron: ").append(job.getCronExpression().getExpression()).append("\n");
            result.append("  command: ").append(command).append("\n");
            result.append("  status: ").append(job.getStatus()).append("\n");
            if (job.getNextFireTime() != null) {
                result.append("  next_fire_time: ").append(job.getNextFireTime());
            }
            return ToolResult.success(result.toString());
        } catch (IllegalArgumentException e) {
            return ToolResult.error("cron 表达式格式错误: " + e.getMessage());
        }
    }

    private ToolResult handleList() {
        List<CronJob> jobs = cronScheduler.listJobs();
        if (jobs.isEmpty()) {
            return ToolResult.success("当前没有注册的定时任务");
        }

        StringJoiner joiner = new StringJoiner("\n---\n");
        joiner.add("共 " + jobs.size() + " 个定时任务:");

        for (CronJob job : jobs) {
            StringBuilder entry = new StringBuilder();
            entry.append("  job_id: ").append(job.getJobId()).append("\n");
            entry.append("  name: ").append(job.getName()).append("\n");
            entry.append("  cron: ").append(job.getCronExpression().getExpression()).append("\n");
            entry.append("  status: ").append(job.getStatus()).append("\n");
            entry.append("  executions: ").append(job.getExecutionCount());
            if (job.getLastFireTime() != null) {
                entry.append("\n  last_fire_time: ").append(job.getLastFireTime());
            }
            if (job.getNextFireTime() != null) {
                entry.append("\n  next_fire_time: ").append(job.getNextFireTime());
            }
            joiner.add(entry.toString());
        }
        return ToolResult.success(joiner.toString());
    }

    private ToolResult handleRemove(CronToolInput input) {
        String jobId = input.getJob_id();
        if (jobId == null || jobId.isBlank()) {
            return ToolResult.error("remove 操作需要提供 job_id");
        }
        if (cronScheduler.removeJob(jobId)) {
            return ToolResult.success("任务已移除: " + jobId);
        }
        return ToolResult.error("未找到任务: " + jobId);
    }

    private ToolResult handlePause(CronToolInput input) {
        String jobId = input.getJob_id();
        if (jobId == null || jobId.isBlank()) {
            return ToolResult.error("pause 操作需要提供 job_id");
        }
        if (cronScheduler.pauseJob(jobId)) {
            return ToolResult.success("任务已暂停: " + jobId);
        }
        return ToolResult.error("无法暂停任务（不存在或非 ACTIVE 状态）: " + jobId);
    }

    private ToolResult handleResume(CronToolInput input) {
        String jobId = input.getJob_id();
        if (jobId == null || jobId.isBlank()) {
            return ToolResult.error("resume 操作需要提供 job_id");
        }
        if (cronScheduler.resumeJob(jobId)) {
            return ToolResult.success("任务已恢复: " + jobId);
        }
        return ToolResult.error("无法恢复任务（不存在或非 PAUSED 状态）: " + jobId);
    }

    private ToolResult handleTrigger(CronToolInput input, ToolExecutionContext context) {
        String jobId = input.getJob_id();
        if (jobId == null || jobId.isBlank()) {
            return ToolResult.error("trigger 操作需要提供 job_id");
        }

        Optional<CronJob> jobOpt = cronScheduler.getJob(jobId);
        if (jobOpt.isEmpty()) {
            return ToolResult.error("未找到任务: " + jobId);
        }

        if (cronScheduler.triggerNow(jobId)) {
            return ToolResult.success("任务已手动触发: " + jobId + " (" + jobOpt.get().getName() + ")");
        }
        return ToolResult.error("触发任务失败: " + jobId);
    }

    private void executeShellCommand(String command, ToolExecutionContext context) {
        // 执行时再次校验：cron 任务延迟异步触发，不能依赖注册时的一次性检查
        if (context != null && context.getPermissionChecker() != null
                && !context.getPermissionChecker().isCommandAllowed(command)) {
            logger.warn("Cron command blocked by permission checker: {}", command);
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            if (context != null && context.getCwd() != null) {
                pb.directory(context.getCwd().toFile());
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("Cron command timed out after {}s: {}", COMMAND_TIMEOUT_SECONDS, command);
            } else {
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    logger.warn("Cron command exited with code {}: {}\n{}", exitCode, command, output);
                } else {
                    logger.debug("Cron command completed: {}", command);
                }
            }
        } catch (Exception e) {
            logger.error("Cron command execution failed: {}", command, e);
        }
    }
}

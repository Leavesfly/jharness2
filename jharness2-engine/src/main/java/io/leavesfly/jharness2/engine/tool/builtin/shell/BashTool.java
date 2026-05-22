package io.leavesfly.jharness2.engine.tool.builtin.shell;

import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.engine.tool.ToolResult;
import io.leavesfly.jharness2.engine.tool.input.BashInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Bash 工具 - 执行 shell 命令并返回输出。
 */
public class BashTool extends BaseTool<BashInput> {

    private static final Logger logger = LoggerFactory.getLogger(BashTool.class);
    private static final int MAX_OUTPUT_LENGTH = 10000;
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("rm\\s+-rf?\\s+/(?![.\\w])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("mkfs\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("dd\\s+if=.*of=/dev/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:shutdown|reboot|halt|poweroff)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:curl|wget)\\s[^|;]*\\|\\s*(?:ba)?sh\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsudo\\s+rm\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile(">\\s*/dev/sd[a-z]", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String getName() { return "bash"; }

    @Override
    public String getDescription() { return "执行 shell 命令并返回输出结果。支持所有标准 Unix 命令。"; }

    @Override
    public Class<BashInput> getInputClass() { return BashInput.class; }

    @Override
    public CompletableFuture<ToolResult> execute(BashInput input, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String command = input.getCommand();
                if (command == null || command.isBlank()) {
                    return ToolResult.error("命令不能为空");
                }
                if (command.length() > 10000) {
                    return ToolResult.error("安全限制: 命令长度超过限制");
                }
                if (isDangerous(command)) {
                    return ToolResult.error("安全限制: 检测到高危命令模式，已拒绝执行");
                }

                int timeout = input.getTimeout_seconds() != null ? input.getTimeout_seconds() : DEFAULT_TIMEOUT_SECONDS;

                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.directory(context.getCwd().toFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() + line.length() > MAX_OUTPUT_LENGTH) {
                            output.append("\n...(输出已截断)");
                            break;
                        }
                        output.append(line).append("\n");
                    }
                }

                boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return ToolResult.error("命令执行超时 (" + timeout + "秒)");
                }

                int exitCode = process.exitValue();
                String result = output.toString();
                if (exitCode != 0) {
                    return ToolResult.error("退出码: " + exitCode + "\n" + result);
                }
                return ToolResult.success(result);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                logger.error("命令执行失败", e);
                return ToolResult.error("命令执行失败: " + e.getMessage());
            }
        });
    }

    @Override
    public boolean isReadOnly(BashInput input) {
        return false;
    }

    private static boolean isDangerous(String command) {
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) return true;
        }
        return false;
    }
}

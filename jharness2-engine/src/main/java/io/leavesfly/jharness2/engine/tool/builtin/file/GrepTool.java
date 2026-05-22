package io.leavesfly.jharness2.engine.tool.builtin.file;

import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.engine.tool.ToolResult;
import io.leavesfly.jharness2.engine.tool.input.GrepInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Grep 内容搜索工具 - 在文件中搜索文本，支持正则。
 */
public class GrepTool extends BaseTool<GrepInput> {

    private static final Logger logger = LoggerFactory.getLogger(GrepTool.class);
    private static final int MAX_RESULTS = 100;
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

    @Override
    public String getName() { return "grep"; }

    @Override
    public String getDescription() { return "在文件内容中搜索文本。支持正则表达式。"; }

    @Override
    public Class<GrepInput> getInputClass() { return GrepInput.class; }

    @Override
    public boolean isReadOnly(GrepInput input) { return true; }

    @Override
    public CompletableFuture<ToolResult> execute(GrepInput input, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String searchPath = input.getPath() != null ? input.getPath() : ".";
                Path basePath = context.getCwd().resolve(searchPath).normalize();
                Pattern pattern = Pattern.compile(input.getPattern(), Pattern.CASE_INSENSITIVE);

                List<String> results = new ArrayList<>();
                int[] count = {0};

                Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (count[0] >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                        if (!input.getInclude_hidden() && file.getFileName().toString().startsWith(".")) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (attrs.size() > MAX_FILE_SIZE_BYTES) return FileVisitResult.CONTINUE;

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
                            String line;
                            int lineNumber = 0;
                            while ((line = reader.readLine()) != null) {
                                lineNumber++;
                                if (pattern.matcher(line).find()) {
                                    Path relativePath = context.getCwd().relativize(file);
                                    results.add(relativePath + ":" + lineNumber + ":" + line);
                                    count[0]++;
                                    if (count[0] >= MAX_RESULTS) break;
                                }
                            }
                        } catch (IOException ignored) {}
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });

                if (results.isEmpty()) return ToolResult.success("未找到匹配的内容");
                String output = String.join("\n", results);
                if (count[0] >= MAX_RESULTS) output += "\n...(已达到结果限制)";
                return ToolResult.success(output);
            } catch (IOException e) {
                logger.error("Grep搜索失败", e);
                return ToolResult.error("Grep搜索失败: " + e.getMessage());
            }
        });
    }
}

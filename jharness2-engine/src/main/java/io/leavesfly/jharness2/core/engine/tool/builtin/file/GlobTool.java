package io.leavesfly.jharness2.core.engine.tool.builtin.file;

import io.leavesfly.jharness2.core.engine.tool.BaseTool;
import io.leavesfly.jharness2.core.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.core.engine.tool.ToolResult;
import io.leavesfly.jharness2.core.engine.tool.input.GlobInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Glob 文件搜索工具 - 按 glob 模式查找文件路径。
 */
public class GlobTool extends BaseTool<GlobInput> {

    private static final Logger logger = LoggerFactory.getLogger(GlobTool.class);
    private static final int MAX_RESULTS = 200;

    @Override
    public String getName() { return "glob"; }

    @Override
    public String getDescription() { return "按 glob 模式搜索文件路径。返回匹配的文件列表。"; }

    @Override
    public Class<GlobInput> getInputClass() { return GlobInput.class; }

    @Override
    public boolean isReadOnly(GlobInput input) { return true; }

    @Override
    public CompletableFuture<ToolResult> execute(GlobInput input, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String searchPath = input.getPath() != null ? input.getPath() : ".";
                Path basePath = context.getCwd().resolve(searchPath).normalize();
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + input.getPattern());

                List<String> results = new ArrayList<>();
                Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (results.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                        Path relative = context.getCwd().relativize(file);
                        if (matcher.matches(relative) || matcher.matches(file.getFileName())) {
                            results.add(relative.toString());
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (dir.getFileName() != null && dir.getFileName().toString().startsWith(".")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });

                if (results.isEmpty()) return ToolResult.success("未找到匹配的文件");
                String output = String.join("\n", results);
                if (results.size() >= MAX_RESULTS) output += "\n...(已达到结果限制)";
                return ToolResult.success(output);
            } catch (IOException e) {
                logger.error("Glob搜索失败", e);
                return ToolResult.error("Glob搜索失败: " + e.getMessage());
            }
        });
    }
}

package io.leavesfly.jharness2.engine.tool.builtin.file;

import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.engine.tool.ToolResult;
import io.leavesfly.jharness2.engine.tool.input.FileReadInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 文件读取工具 - 读取文件内容，支持按行范围读取。
 */
public class FileReadTool extends BaseTool<FileReadInput> {

    private static final Logger logger = LoggerFactory.getLogger(FileReadTool.class);
    private static final int MAX_LINE_LENGTH = 2000;
    private static final int MAX_LINES = 500;

    @Override
    public String getName() { return "read_file"; }

    @Override
    public String getDescription() { return "读取文件内容。支持指定起始行和行数限制。"; }

    @Override
    public Class<FileReadInput> getInputClass() { return FileReadInput.class; }

    @Override
    public boolean isReadOnly(FileReadInput input) { return true; }

    @Override
    public CompletableFuture<ToolResult> execute(FileReadInput input, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = context.getCwd().resolve(input.getFile_path()).normalize();

                if (!filePath.startsWith(context.getCwd().toAbsolutePath().normalize())) {
                    return ToolResult.error("安全限制: 不允许访问工作目录之外的文件");
                }
                if (!Files.exists(filePath)) {
                    return ToolResult.error("文件不存在: " + filePath);
                }
                if (!Files.isRegularFile(filePath)) {
                    return ToolResult.error("不是普通文件: " + filePath);
                }

                int offset = input.getOffset() != null ? input.getOffset() : 1;
                int limit = input.getLimit() != null ? input.getLimit() : MAX_LINES;

                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                    String line;
                    int lineNum = 0;
                    while ((line = reader.readLine()) != null) {
                        lineNum++;
                        if (lineNum < offset) continue;
                        if (lineNum >= offset + limit) {
                            content.append("...(已达到行数限制，共").append(limit).append("行)\n");
                            break;
                        }
                        if (line.length() > MAX_LINE_LENGTH) {
                            line = line.substring(0, MAX_LINE_LENGTH) + "...(行已截断)";
                        }
                        content.append(lineNum).append("\t").append(line).append("\n");
                    }
                }
                return ToolResult.success(content.toString());
            } catch (IOException e) {
                logger.error("读取文件失败", e);
                return ToolResult.error("读取文件失败: " + e.getMessage());
            }
        });
    }
}

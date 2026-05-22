package io.leavesfly.jharness2.core.engine.tool.builtin.file;

import io.leavesfly.jharness2.core.engine.tool.BaseTool;
import io.leavesfly.jharness2.core.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.core.engine.tool.ToolResult;
import io.leavesfly.jharness2.core.engine.tool.input.FileWriteInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 文件写入工具 - 创建或覆盖文件内容。
 */
public class FileWriteTool extends BaseTool<FileWriteInput> {

    private static final Logger logger = LoggerFactory.getLogger(FileWriteTool.class);

    @Override
    public String getName() { return "write_file"; }

    @Override
    public String getDescription() { return "创建或覆盖文件内容。会自动创建父目录。"; }

    @Override
    public Class<FileWriteInput> getInputClass() { return FileWriteInput.class; }

    @Override
    public CompletableFuture<ToolResult> execute(FileWriteInput input, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = context.getCwd().resolve(input.getFile_path()).normalize();

                if (!filePath.startsWith(context.getCwd().toAbsolutePath().normalize())) {
                    return ToolResult.error("安全限制: 不允许写入工作目录之外的文件");
                }

                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, input.getContent(), StandardCharsets.UTF_8);

                return ToolResult.success("文件已写入: " + filePath);
            } catch (IOException e) {
                logger.error("写入文件失败", e);
                return ToolResult.error("写入文件失败: " + e.getMessage());
            }
        });
    }
}

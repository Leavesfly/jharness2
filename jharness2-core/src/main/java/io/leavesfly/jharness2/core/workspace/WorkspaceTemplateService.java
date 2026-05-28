package io.leavesfly.jharness2.core.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Workspace 模板服务 —— 支持从预置模板初始化新的 workspace。
 * <p>
 * 模板可包含预设的文件结构、配置文件、.jharness 目录等，
 * 让新用户/新会话能快速进入工作状态。
 */
public class WorkspaceTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceTemplateService.class);

    private final Path templateDirectory;

    public WorkspaceTemplateService(Path templateDirectory) {
        this.templateDirectory = templateDirectory;
    }

    /**
     * 获取可用模板列表。
     */
    public List<String> listTemplates() {
        if (!Files.isDirectory(templateDirectory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(templateDirectory)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.warn("Failed to list templates: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将指定模板的内容复制到目标 workspace。
     *
     * @param templateName 模板名称
     * @param targetPath   目标 workspace 路径
     * @return 复制的文件数
     */
    public int applyTemplate(String templateName, Path targetPath) {
        Path templatePath = templateDirectory.resolve(templateName);
        if (!Files.isDirectory(templatePath)) {
            logger.warn("Template not found: {}", templateName);
            return 0;
        }

        try {
            int[] count = {0};
            Files.walkFileTree(templatePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = templatePath.relativize(dir);
                    Path targetDir = targetPath.resolve(relative);
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = templatePath.relativize(file);
                    Path targetFile = targetPath.resolve(relative);
                    if (!Files.exists(targetFile)) {
                        Files.copy(file, targetFile);
                        count[0]++;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            logger.info("Applied template '{}' to {}: {} files copied",
                    templateName, targetPath, count[0]);
            return count[0];
        } catch (IOException e) {
            logger.error("Failed to apply template '{}': {}", templateName, e.getMessage());
            return 0;
        }
    }

    /**
     * 检查模板是否存在。
     */
    public boolean templateExists(String templateName) {
        return Files.isDirectory(templateDirectory.resolve(templateName));
    }
}

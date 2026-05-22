package io.leavesfly.jharness2.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 自动创建用户工作空间目录。
 * 在 Engine 创建前确保用户的隔离 workspace 存在。
 */
@Component
public class WorkspaceInitializer {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceInitializer.class);

    private final Path workspaceRoot;

    public WorkspaceInitializer(
            @Value("${jharness2.workspace.root:./data/workspaces}") String workspaceRoot) {
        this.workspaceRoot = Paths.get(workspaceRoot);
        ensureRootExists();
    }

    private void ensureRootExists() {
        try {
            Files.createDirectories(workspaceRoot);
            logger.info("Workspace root ensured: {}", workspaceRoot.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create workspace root: {}", workspaceRoot, e);
        }
    }

    /**
     * 确保用户工作空间存在，返回其路径。
     */
    public Path ensureUserWorkspace(String userId) {
        Path userDir = workspaceRoot.resolve(sanitize(userId));
        if (!Files.exists(userDir)) {
            try {
                Files.createDirectories(userDir);
                logger.info("Created workspace for user={}: {}", userId, userDir);
            } catch (IOException e) {
                logger.error("Failed to create workspace for user={}", userId, e);
                throw new RuntimeException("Cannot create workspace for user: " + userId, e);
            }
        }
        return userDir;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

package io.leavesfly.jharness2.core.workspace;

import io.leavesfly.jharness2.core.spi.WorkspaceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件系统工作空间存储（默认实现）。
 * <p>
 * 直接使用本地磁盘目录，sync 方法均为 no-op。
 */
public class LocalWorkspaceStorage implements WorkspaceStorage {

    private static final Logger logger = LoggerFactory.getLogger(LocalWorkspaceStorage.class);

    private final Path workspaceRoot;

    public LocalWorkspaceStorage(String rootPath) {
        this.workspaceRoot = Paths.get(rootPath);
        ensureRootExists();
    }

    private void ensureRootExists() {
        try {
            Files.createDirectories(workspaceRoot);
            logger.info("Local workspace root ensured: {}", workspaceRoot.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create workspace root: {}", workspaceRoot, e);
        }
    }

    @Override
    public Path ensureUserWorkspace(String userId) {
        Path userDir = workspaceRoot.resolve(sanitize(userId));
        if (!Files.exists(userDir)) {
            try {
                Files.createDirectories(userDir);
                logger.info("Created local workspace for user={}: {}", userId, userDir);
            } catch (IOException e) {
                logger.error("Failed to create workspace for user={}", userId, e);
                throw new RuntimeException("Cannot create workspace for user: " + userId, e);
            }
        }
        return userDir;
    }

    @Override
    public void syncToRemote(String userId) {
        // no-op for local storage
    }

    @Override
    public void syncFromRemote(String userId) {
        // no-op for local storage
    }

    @Override
    public void release(String userId, boolean deleteLocalCache) {
        // no-op for local storage (never delete user workspace)
    }

    @Override
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    private String sanitize(String input) {
        return WorkspacePathNaming.toDirectoryName(input);
    }
}

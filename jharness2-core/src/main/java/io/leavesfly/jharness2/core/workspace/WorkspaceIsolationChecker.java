package io.leavesfly.jharness2.core.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Workspace 隔离检查器 —— 确保用户文件操作严格隔离在各自 workspace 下。
 * <p>
 * 防止路径穿越攻击（path traversal），保证用户无法访问其他用户的 workspace。
 */
public class WorkspaceIsolationChecker {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceIsolationChecker.class);

    private final Path workspaceRoot;
    private final boolean enabled;

    public WorkspaceIsolationChecker(Path workspaceRoot, boolean enabled) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.enabled = enabled;
    }

    /**
     * 检查目标路径是否在指定用户的 workspace 范围内。
     *
     * @param userId     用户标识
     * @param targetPath 要访问的目标路径
     * @return true 如果路径合法
     */
    public boolean isPathAllowed(String userId, Path targetPath) {
        if (!enabled) {
            return true;
        }

        Path userWorkspace = workspaceRoot.resolve(userId).toAbsolutePath().normalize();
        Path normalizedTarget = targetPath.toAbsolutePath().normalize();

        boolean allowed = normalizedTarget.startsWith(userWorkspace);
        if (!allowed) {
            logger.warn("Path isolation violation: user={} attempted to access path={} outside workspace={}",
                    userId, normalizedTarget, userWorkspace);
        }
        return allowed;
    }

    /**
     * 检查路径是否存在穿越风险（包含 .. 等）。
     */
    public boolean hasTraversalRisk(String pathString) {
        return pathString.contains("..") || pathString.contains("~");
    }

    /**
     * 解析用户相对路径为绝对路径，并验证隔离性。
     *
     * @param userId       用户标识
     * @param relativePath 相对路径
     * @return 验证后的绝对路径
     * @throws SecurityException 如果路径违反隔离策略
     */
    public Path resolveAndValidate(String userId, String relativePath) {
        if (hasTraversalRisk(relativePath)) {
            throw new SecurityException(
                    "Path contains traversal characters: " + relativePath);
        }

        Path userWorkspace = workspaceRoot.resolve(userId).toAbsolutePath().normalize();
        Path resolved = userWorkspace.resolve(relativePath).toAbsolutePath().normalize();

        if (!resolved.startsWith(userWorkspace)) {
            throw new SecurityException(
                    "Path escapes user workspace boundary: " + relativePath);
        }

        return resolved;
    }
}

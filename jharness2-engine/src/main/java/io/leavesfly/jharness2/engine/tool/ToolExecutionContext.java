package io.leavesfly.jharness2.engine.tool;

import io.leavesfly.jharness2.engine.policy.access.PermissionChecker;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行上下文，提供工作目录、元数据和权限检查器。
 */
public class ToolExecutionContext {
    private final Path cwd;
    private final Map<String, Object> metadata;
    private final PermissionChecker permissionChecker;

    public ToolExecutionContext(Path cwd, Map<String, Object> metadata, PermissionChecker permissionChecker) {
        this.cwd = cwd;
        this.metadata = metadata != null ? new ConcurrentHashMap<>(metadata) : new ConcurrentHashMap<>();
        this.permissionChecker = permissionChecker;
    }

    public ToolExecutionContext(Path cwd, PermissionChecker permissionChecker) {
        this(cwd, null, permissionChecker);
    }

    public Path getCwd() { return cwd; }
    public Map<String, Object> getMetadata() { return metadata; }
    public PermissionChecker getPermissionChecker() { return permissionChecker; }
}

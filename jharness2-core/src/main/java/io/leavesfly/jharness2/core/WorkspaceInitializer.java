package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.core.spi.WorkspaceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 用户工作空间初始化器。
 * 委托给 {@link WorkspaceStorage} SPI 实现，支持本地 / OSS 等多种后端。
 */
@Component
public class WorkspaceInitializer {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceInitializer.class);

    private final WorkspaceStorage workspaceStorage;

    public WorkspaceInitializer(WorkspaceStorage workspaceStorage) {
        this.workspaceStorage = workspaceStorage;
        logger.info("WorkspaceInitializer using storage: {}", workspaceStorage.getClass().getSimpleName());
    }

    /**
     * 确保用户工作空间可用，返回其本地可操作路径。
     */
    public Path ensureUserWorkspace(String userId) {
        return workspaceStorage.ensureUserWorkspace(userId);
    }

    /**
     * 将用户工作空间变更同步到远端（本地模式为 no-op）。
     */
    public void syncToRemote(String userId) {
        workspaceStorage.syncToRemote(userId);
    }

    /**
     * 从远端拉取最新内容到本地（本地模式为 no-op）。
     */
    public void syncFromRemote(String userId) {
        workspaceStorage.syncFromRemote(userId);
    }

    /**
     * 释放用户工作空间本地缓存。
     */
    public void release(String userId, boolean deleteLocalCache) {
        workspaceStorage.release(userId, deleteLocalCache);
    }

    public Path getWorkspaceRoot() {
        return workspaceStorage.getWorkspaceRoot();
    }
}

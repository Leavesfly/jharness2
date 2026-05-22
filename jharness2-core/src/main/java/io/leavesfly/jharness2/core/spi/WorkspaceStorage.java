package io.leavesfly.jharness2.core.spi;

import java.nio.file.Path;

/**
 * 工作空间存储 SPI 接口。
 * <p>
 * 抽象用户工作空间的生命周期管理，支持不同的后端存储实现：
 * - 本地文件系统（默认）
 * - 对象存储（OSS/S3）+ 本地缓存
 * - 共享文件系统（NFS/JuiceFS）
 * <p>
 * 核心契约：调用方拿到的 Path 始终是本地可操作的路径（工具层零侵入），
 * 后端负责将本地变更持久化到远端存储。
 */
public interface WorkspaceStorage {

    /**
     * 确保用户工作空间可用，返回其本地路径。
     * <p>
     * 对于 OSS 实现：首次调用时从远端下载到本地缓存目录。
     * 对于本地实现：确保目录存在即可。
     *
     * @param userId 用户标识
     * @return 用户工作空间的本地可操作路径
     */
    Path ensureUserWorkspace(String userId);

    /**
     * 将用户工作空间的本地变更同步到远端存储。
     * <p>
     * 本地实现为 no-op；OSS 实现会上传增量变更。
     *
     * @param userId 用户标识
     */
    void syncToRemote(String userId);

    /**
     * 从远端存储拉取最新内容到本地缓存。
     * <p>
     * 本地实现为 no-op；OSS 实现会下载远端新文件。
     *
     * @param userId 用户标识
     */
    void syncFromRemote(String userId);

    /**
     * 释放用户工作空间的本地缓存（可选）。
     * <p>
     * 在引擎驱逐/关闭时调用，用于清理本地缓存空间。
     * 本地实现为 no-op；OSS 实现可选择删除本地缓存目录。
     *
     * @param userId 用户标识
     * @param deleteLocalCache 是否删除本地缓存
     */
    void release(String userId, boolean deleteLocalCache);

    /**
     * 获取工作空间根目录。
     */
    Path getWorkspaceRoot();
}

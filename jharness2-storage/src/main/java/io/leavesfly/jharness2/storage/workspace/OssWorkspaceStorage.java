package io.leavesfly.jharness2.storage.workspace;

import io.leavesfly.jharness2.core.spi.WorkspaceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 基于对象存储（OSS/S3）的工作空间存储实现。
 * <p>
 * 策略：
 * - 本地缓存目录作为工作路径（对工具层透明）
 * - 首次 ensureUserWorkspace 时从 OSS 下载用户目录到本地缓存
 * - syncToRemote 时将本地变更增量上传到 OSS
 * - syncFromRemote 时从 OSS 拉取远端新文件
 * <p>
 * 对象存储操作通过 {@link OssClient} 接口抽象，支持 Aliyun OSS / AWS S3 等。
 */
public class OssWorkspaceStorage implements WorkspaceStorage {

    private static final Logger logger = LoggerFactory.getLogger(OssWorkspaceStorage.class);

    private final OssClient ossClient;
    private final Path localCacheRoot;
    private final String keyPrefix;
    private final boolean deleteLocalCacheOnRelease;
    private final Set<String> initializedUsers = ConcurrentHashMap.newKeySet();

    public OssWorkspaceStorage(OssClient ossClient, OssWorkspaceConfig config) {
        this.ossClient = ossClient;
        this.localCacheRoot = Paths.get(config.getLocalCacheDir());
        this.keyPrefix = config.getKeyPrefix().endsWith("/")
                ? config.getKeyPrefix() : config.getKeyPrefix() + "/";
        this.deleteLocalCacheOnRelease = config.isDeleteLocalCacheOnRelease();

        try {
            Files.createDirectories(localCacheRoot);
            logger.info("OSS workspace cache root ensured: {}", localCacheRoot.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create OSS cache root: {}", localCacheRoot, e);
        }
    }

    @Override
    public Path ensureUserWorkspace(String userId) {
        String sanitized = sanitize(userId);
        Path localDir = localCacheRoot.resolve(sanitized);

        try {
            Files.createDirectories(localDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create local cache dir for user: " + userId, e);
        }

        // 首次访问时从 OSS 同步到本地
        if (initializedUsers.add(sanitized)) {
            syncFromRemote(userId);
            logger.info("Initialized workspace from OSS for user={}, local={}", userId, localDir);
        }

        return localDir;
    }

    @Override
    public void syncToRemote(String userId) {
        String sanitized = sanitize(userId);
        Path localDir = localCacheRoot.resolve(sanitized);
        if (!Files.isDirectory(localDir)) return;

        String userPrefix = keyPrefix + sanitized + "/";

        try (Stream<Path> walker = Files.walk(localDir)) {
            walker.filter(Files::isRegularFile).forEach(localFile -> {
                String relativePath = localDir.relativize(localFile).toString();
                String ossKey = userPrefix + relativePath.replace('\\', '/');
                try {
                    ossClient.putObject(ossKey, localFile);
                } catch (Exception e) {
                    logger.warn("Failed to upload file to OSS: key={}", ossKey, e);
                }
            });
        } catch (IOException e) {
            logger.error("Failed to walk local workspace for sync: user={}", userId, e);
        }

        logger.debug("Synced workspace to OSS for user={}", userId);
    }

    @Override
    public void syncFromRemote(String userId) {
        String sanitized = sanitize(userId);
        Path localDir = localCacheRoot.resolve(sanitized);
        String userPrefix = keyPrefix + sanitized + "/";

        try {
            Files.createDirectories(localDir);
            var objects = ossClient.listObjects(userPrefix);
            for (String ossKey : objects) {
                String relativePath = ossKey.substring(userPrefix.length());
                if (relativePath.isEmpty()) continue;

                Path localFile = localDir.resolve(relativePath);
                Files.createDirectories(localFile.getParent());

                // 仅下载本地不存在或远端更新的文件
                if (!Files.exists(localFile)) {
                    try (InputStream is = ossClient.getObject(ossKey);
                         OutputStream os = Files.newOutputStream(localFile)) {
                        is.transferTo(os);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to sync workspace from OSS: user={}", userId, e);
        }

        logger.debug("Synced workspace from OSS for user={}", userId);
    }

    @Override
    public void release(String userId, boolean deleteLocalCache) {
        String sanitized = sanitize(userId);
        initializedUsers.remove(sanitized);

        if (deleteLocalCache || deleteLocalCacheOnRelease) {
            Path localDir = localCacheRoot.resolve(sanitized);
            if (Files.isDirectory(localDir)) {
                try {
                    deleteRecursively(localDir);
                    logger.info("Deleted local cache for user={}", userId);
                } catch (IOException e) {
                    logger.warn("Failed to delete local cache for user={}", userId, e);
                }
            }
        }
    }

    @Override
    public Path getWorkspaceRoot() {
        return localCacheRoot;
    }

    private void deleteRecursively(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 同步所有已初始化用户的 workspace 到 OSS（由定时调度器调用）。
     */
    public void syncAllInitializedUsers() {
        for (String sanitizedUserId : initializedUsers) {
            try {
                syncToRemote(sanitizedUserId);
            } catch (Exception e) {
                logger.warn("Scheduled sync failed for user={}", sanitizedUserId, e);
            }
        }
        logger.debug("Scheduled OSS sync completed for {} users", initializedUsers.size());
    }

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

package io.leavesfly.jharness2.core.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Workspace 容量检查器 —— 监控和限制单个 workspace 的存储容量。
 */
public class WorkspaceCapacityChecker {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceCapacityChecker.class);

    private final long maxSizeBytes;
    private final double cleanupThreshold;

    public WorkspaceCapacityChecker(long maxSizeMb, double cleanupThreshold) {
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
        this.cleanupThreshold = cleanupThreshold;
    }

    /**
     * 计算指定路径下的总文件大小。
     */
    public long calculateSize(Path workspace) {
        if (!Files.isDirectory(workspace)) {
            return 0;
        }

        AtomicLong totalSize = new AtomicLong(0);
        try {
            Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    totalSize.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to calculate workspace size: {}", e.getMessage());
        }
        return totalSize.get();
    }

    /**
     * 检查 workspace 是否超出容量限制。
     */
    public CapacityStatus checkCapacity(Path workspace) {
        long currentSize = calculateSize(workspace);
        double usageRatio = maxSizeBytes > 0 ? (double) currentSize / maxSizeBytes : 0;

        if (usageRatio >= 1.0) {
            return new CapacityStatus(currentSize, maxSizeBytes, usageRatio, CapacityLevel.EXCEEDED);
        }
        if (usageRatio >= cleanupThreshold) {
            return new CapacityStatus(currentSize, maxSizeBytes, usageRatio, CapacityLevel.WARNING);
        }
        return new CapacityStatus(currentSize, maxSizeBytes, usageRatio, CapacityLevel.NORMAL);
    }

    /**
     * 是否允许写入指定大小的数据。
     */
    public boolean canWrite(Path workspace, long additionalBytes) {
        long currentSize = calculateSize(workspace);
        return (currentSize + additionalBytes) <= maxSizeBytes;
    }

    public enum CapacityLevel {
        NORMAL, WARNING, EXCEEDED
    }

    public record CapacityStatus(long currentBytes, long maxBytes, double usageRatio, CapacityLevel level) {
        public boolean isExceeded() { return level == CapacityLevel.EXCEEDED; }
        public boolean needsCleanup() { return level == CapacityLevel.WARNING || level == CapacityLevel.EXCEEDED; }
    }
}

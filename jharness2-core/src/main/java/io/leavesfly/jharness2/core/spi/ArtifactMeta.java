package io.leavesfly.jharness2.core.spi;

import java.time.Instant;

/**
 * 文件工件元数据。
 */
public record ArtifactMeta(
    String artifactId,
    String userId,
    String sessionId,
    String filePath,
    String mimeType,
    long sizeBytes,
    String checksum,
    Instant createdAt
) {}

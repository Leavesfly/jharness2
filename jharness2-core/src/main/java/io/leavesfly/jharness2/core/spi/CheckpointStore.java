package io.leavesfly.jharness2.core.spi;

import io.leavesfly.jharness2.core.checkpoint.CheckpointData;

import java.util.Optional;

/**
 * Checkpoint 存储 SPI —— 允许不同后端实现（内存、文件、Redis 等）。
 */
public interface CheckpointStore {

    /**
     * 保存 checkpoint。
     */
    void save(CheckpointData checkpoint);

    /**
     * 加载指定 session 的最新 checkpoint。
     */
    Optional<CheckpointData> loadLatest(String userId, String sessionId);

    /**
     * 删除指定 session 的所有 checkpoint。
     */
    void deleteAll(String userId, String sessionId);
}

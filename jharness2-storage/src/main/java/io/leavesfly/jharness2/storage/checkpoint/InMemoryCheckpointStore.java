package io.leavesfly.jharness2.storage.checkpoint;

import io.leavesfly.jharness2.core.checkpoint.CheckpointData;
import io.leavesfly.jharness2.core.spi.CheckpointStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的 Checkpoint 存储（默认实现，适合单机场景）。
 */
public class InMemoryCheckpointStore implements CheckpointStore {

    private final ConcurrentHashMap<String, Deque<CheckpointData>> store = new ConcurrentHashMap<>();
    private final int maxPerSession;

    public InMemoryCheckpointStore(int maxPerSession) {
        this.maxPerSession = maxPerSession;
    }

    @Override
    public void save(CheckpointData checkpoint) {
        String key = checkpoint.getCacheKey();
        store.compute(key, (k, deque) -> {
            if (deque == null) {
                deque = new ArrayDeque<>();
            }
            deque.addLast(checkpoint);
            while (deque.size() > maxPerSession) {
                deque.pollFirst();
            }
            return deque;
        });
    }

    @Override
    public Optional<CheckpointData> loadLatest(String userId, String sessionId) {
        String key = userId + ":" + sessionId;
        Deque<CheckpointData> deque = store.get(key);
        if (deque == null || deque.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(deque.peekLast());
    }

    @Override
    public void deleteAll(String userId, String sessionId) {
        store.remove(userId + ":" + sessionId);
    }
}

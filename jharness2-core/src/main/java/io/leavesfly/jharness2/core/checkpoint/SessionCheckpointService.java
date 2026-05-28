package io.leavesfly.jharness2.core.checkpoint;

import io.leavesfly.jharness2.core.EngineInstance;
import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.spi.CheckpointStore;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Session Checkpoint 服务 —— 定期自动快照引擎状态。
 * <p>
 * 支持按 turn 数触发和手动触发两种方式。
 * 用于优雅关闭前的状态保存和快速恢复。
 */
public class SessionCheckpointService {

    private static final Logger logger = LoggerFactory.getLogger(SessionCheckpointService.class);

    private final CheckpointStore checkpointStore;
    private final CheckpointConfig config;
    private final ConcurrentHashMap<String, AtomicInteger> turnCounters = new ConcurrentHashMap<>();

    public SessionCheckpointService(CheckpointStore checkpointStore, CheckpointConfig config) {
        this.checkpointStore = checkpointStore;
        this.config = config;
    }

    /**
     * 在每次 turn 完成后调用，根据配置判断是否需要保存 checkpoint。
     */
    public void onTurnCompleted(EngineInstance instance) {
        if (!config.isEnabled()) {
            return;
        }

        String cacheKey = instance.getUserContext().getCacheKey();
        AtomicInteger counter = turnCounters.computeIfAbsent(cacheKey, k -> new AtomicInteger(0));
        int currentTurn = counter.incrementAndGet();

        if (currentTurn % config.getIntervalTurns() == 0) {
            checkpoint(instance);
        }
    }

    /**
     * 手动触发 checkpoint（如优雅关闭前）。
     */
    public void checkpoint(EngineInstance instance) {
        try {
            UserContext context = instance.getUserContext();
            QueryEngine engine = instance.getEngine();
            List<ConversationMessage> messages = engine.getMessages();

            if (messages.isEmpty()) {
                return;
            }

            String cacheKey = context.getCacheKey();
            AtomicInteger counter = turnCounters.get(cacheKey);
            int turnCount = counter != null ? counter.get() : 0;

            CheckpointData checkpoint = new CheckpointData(
                    context.getUserId(),
                    context.getSessionId(),
                    messages,
                    engine.getCostTracker().getInputTokens(),
                    engine.getCostTracker().getOutputTokens(),
                    turnCount
            );

            checkpointStore.save(checkpoint);
            logger.debug("Checkpoint saved for session={}, turns={}, messages={}",
                    cacheKey, turnCount, messages.size());
        } catch (Exception e) {
            logger.warn("Failed to save checkpoint for engine: {}",
                    instance.getUserContext().getCacheKey(), e);
        }
    }

    /**
     * 加载最新 checkpoint 用于恢复。
     */
    public Optional<CheckpointData> loadLatest(String userId, String sessionId) {
        return checkpointStore.loadLatest(userId, sessionId);
    }

    /**
     * 清理指定 session 的所有 checkpoint。
     */
    public void cleanup(String userId, String sessionId) {
        checkpointStore.deleteAll(userId, sessionId);
        turnCounters.remove(userId + ":" + sessionId);
    }
}

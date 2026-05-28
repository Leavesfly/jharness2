package io.leavesfly.jharness2.core.session;

import io.leavesfly.jharness2.core.EngineFactory;
import io.leavesfly.jharness2.core.EngineInstance;
import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.checkpoint.CheckpointData;
import io.leavesfly.jharness2.core.checkpoint.SessionCheckpointService;
import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Session 恢复服务 —— 用户重连时从 checkpoint 或持久化存储快速恢复引擎。
 * <p>
 * 恢复优先级：
 * 1. 内存中的活跃引擎（由 UserEngineRegistry 管理）
 * 2. Checkpoint 快照（最近的状态快照）
 * 3. 持久化存储（完整的会话历史）
 */
public class SessionResumeService {

    private static final Logger logger = LoggerFactory.getLogger(SessionResumeService.class);

    private final SessionCheckpointService checkpointService;
    private final EngineFactory engineFactory;

    public SessionResumeService(SessionCheckpointService checkpointService,
                                EngineFactory engineFactory) {
        this.checkpointService = checkpointService;
        this.engineFactory = engineFactory;
    }

    /**
     * 尝试从 checkpoint 恢复引擎实例。
     *
     * @param context 用户上下文
     * @return 恢复的引擎实例（如果有可用 checkpoint），否则 empty
     */
    public Optional<EngineInstance> resumeFromCheckpoint(UserContext context) {
        Optional<CheckpointData> checkpoint = checkpointService.loadLatest(
                context.getUserId(), context.getSessionId());

        if (checkpoint.isEmpty()) {
            logger.debug("No checkpoint found for user={}, session={}",
                    context.getUserId(), context.getSessionId());
            return Optional.empty();
        }

        CheckpointData data = checkpoint.get();
        logger.info("Resuming from checkpoint: user={}, session={}, turns={}, messages={}",
                context.getUserId(), context.getSessionId(),
                data.getTurnCount(), data.getMessages().size());

        EngineInstance restored = engineFactory.restore(
                context,
                data.getMessages(),
                data.getInputTokens(),
                data.getOutputTokens()
        );

        return Optional.of(restored);
    }

    /**
     * 获取 session 的恢复信息摘要（用于前端展示）。
     */
    public Optional<SessionResumeSummary> getResumeSummary(String userId, String sessionId) {
        Optional<CheckpointData> checkpoint = checkpointService.loadLatest(userId, sessionId);
        return checkpoint.map(data -> new SessionResumeSummary(
                data.getUserId(),
                data.getSessionId(),
                data.getMessages().size(),
                data.getTurnCount(),
                data.getInputTokens() + data.getOutputTokens(),
                data.getCreatedAt()
        ));
    }

    /**
     * Session 恢复摘要信息。
     */
    public record SessionResumeSummary(
            String userId,
            String sessionId,
            int messageCount,
            int turnCount,
            long totalTokens,
            java.time.Instant checkpointTime
    ) {}
}

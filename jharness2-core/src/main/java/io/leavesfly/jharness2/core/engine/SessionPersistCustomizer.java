package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import io.leavesfly.jharness2.engine.QueryEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 会话持久化定制器 —— 设置引擎的 SessionPersister。
 */
public class SessionPersistCustomizer implements EngineCustomizer {

    private static final Logger logger = LoggerFactory.getLogger(SessionPersistCustomizer.class);

    private final SessionPersistenceService sessionStorageService;

    public SessionPersistCustomizer(SessionPersistenceService sessionStorageService) {
        this.sessionStorageService = sessionStorageService;
    }

    @Override
    public void customize(QueryEngine engine, UserContext context, Path workspace) {
        String userId = context.getUserId();
        String sessionId = context.getSessionId();
        String model = context.getModel();

        engine.setSessionPersister(messages -> {
            try {
                if (messages == null || messages.isEmpty()) return;
                long inputTokens = engine.getCostTracker().getInputTokens();
                long outputTokens = engine.getCostTracker().getOutputTokens();
                sessionStorageService.saveSession(userId, sessionId, model,
                        messages, inputTokens, outputTokens);
            } catch (Exception e) {
                logger.debug("Auto-save session failed (ignored): user={}, session={}, error={}",
                        userId, sessionId, e.getMessage());
            }
        });
    }

    @Override
    public int getOrder() { return 500; }
}

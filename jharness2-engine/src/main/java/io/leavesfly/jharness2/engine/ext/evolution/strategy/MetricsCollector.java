package io.leavesfly.jharness2.engine.ext.evolution.strategy;

import io.leavesfly.jharness2.engine.ext.hook.HookEvent;
import io.leavesfly.jharness2.engine.ext.hook.HookHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标采集器 —— 通过 Hook 系统被动采集会话执行指标。
 * <p>
 * 注册到 HookExecutor 监听 POST_TOOL_USE 事件，实时记录工具调用序列和错误计数。
 * 在 SESSION_END 时由 EvolutionEngine 读取并 flush 指标。
 */
public class MetricsCollector implements HookHandler {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    /** 当前活跃的 session metrics（sessionId → metrics） */
    private final Map<String, SessionMetrics> activeMetrics = new ConcurrentHashMap<>();

    /**
     * 开始跟踪一个新会话的指标。
     */
    public SessionMetrics startSession(String sessionId, int maxTurns) {
        SessionMetrics metrics = new SessionMetrics(sessionId, maxTurns);
        activeMetrics.put(sessionId, metrics);
        logger.debug("Started metrics collection for session={}", sessionId);
        return metrics;
    }

    /**
     * 获取指定会话的当前指标（不移除）。
     */
    public SessionMetrics getMetrics(String sessionId) {
        return activeMetrics.get(sessionId);
    }

    /**
     * 完成会话指标采集并移除（flush）。
     */
    public SessionMetrics flush(String sessionId) {
        SessionMetrics metrics = activeMetrics.remove(sessionId);
        if (metrics != null) {
            logger.debug("Flushed metrics for session={}: turns={}, tools={}, errors={}",
                    sessionId, metrics.getTurnsUsed(),
                    metrics.getToolSequence().size(), metrics.getToolErrorCount());
        }
        return metrics;
    }

    /**
     * HookHandler 实现 —— 处理 POST_TOOL_USE 事件。
     * <p>
     * 期望 payload 中包含：
     * - "sessionId" → String
     * - "toolName" → String
     * - "success" → Boolean
     */
    @Override
    public void handle(HookEvent event, Map<String, Object> payload) {
        if (!HookEvent.POST_TOOL_USE.equals(event)) return;

        String sessionId = (String) payload.get("sessionId");
        String toolName = (String) payload.get("toolName");
        Boolean success = (Boolean) payload.get("success");

        if (sessionId == null) return;

        SessionMetrics metrics = activeMetrics.get(sessionId);
        if (metrics == null) return;

        if (toolName != null) {
            metrics.recordToolCall(toolName);
        }
        if (Boolean.FALSE.equals(success)) {
            metrics.recordToolError();
        }
    }

    /**
     * 获取当前活跃会话数。
     */
    public int activeSessionCount() {
        return activeMetrics.size();
    }
}

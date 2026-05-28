package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.spi.ToolAuditEntry;
import io.leavesfly.jharness2.core.spi.ToolAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * 引擎定制器 —— 注册工具执行审计回调。
 * <p>
 * 在每次工具执行前后记录审计日志，包括工具名称、参数、结果、耗时和风险等级。
 * 通过 ToolAuditLogger SPI 持久化审计记录。
 */
public class AuditCustomizer {

    private static final Logger logger = LoggerFactory.getLogger(AuditCustomizer.class);
    private final ToolAuditLogger auditLogger;

    public AuditCustomizer(ToolAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * 记录工具执行审计日志。
     * 由引擎工具执行链在工具执行完成后调用。
     */
    public void auditToolExecution(String userId, String sessionId,
                                    String toolName, String arguments,
                                    String result, String status,
                                    long durationMs) {
        String riskLevel = determineRiskLevel(toolName);
        ToolAuditEntry entry = new ToolAuditEntry(
                UUID.randomUUID().toString(),
                userId,
                sessionId,
                toolName,
                arguments,
                result,
                status,
                riskLevel,
                durationMs,
                Instant.now()
        );
        try {
            auditLogger.log(entry);
        } catch (Exception e) {
            logger.warn("Failed to persist audit log for tool={}, user={}: {}",
                    toolName, userId, e.getMessage());
        }
    }

    /**
     * 根据工具名称自动判定风险等级。
     */
    private String determineRiskLevel(String toolName) {
        if (toolName == null) return ToolAuditEntry.RISK_LOW;
        String lower = toolName.toLowerCase();
        if (lower.contains("delete") || lower.contains("launch") || lower.contains("process") || lower.contains("exec")) {
            return ToolAuditEntry.RISK_HIGH;
        }
        if (lower.contains("write") || lower.contains("create") || lower.contains("replace") || lower.contains("fetch")) {
            return ToolAuditEntry.RISK_MEDIUM;
        }
        return ToolAuditEntry.RISK_LOW;
    }
}

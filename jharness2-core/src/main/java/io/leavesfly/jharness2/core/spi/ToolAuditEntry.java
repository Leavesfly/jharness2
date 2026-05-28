package io.leavesfly.jharness2.core.spi;

import java.time.Instant;

/**
 * 工具执行审计日志条目。
 */
public record ToolAuditEntry(
    String id,
    String userId,
    String sessionId,
    String toolName,
    String arguments,
    String result,
    String status,
    String riskLevel,
    long durationMs,
    Instant executedAt
) {
    /** 审计状态常量 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_DENIED = "DENIED";

    /** 风险等级常量 */
    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";
    public static final String RISK_CRITICAL = "CRITICAL";
}

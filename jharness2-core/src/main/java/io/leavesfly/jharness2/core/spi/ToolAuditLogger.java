package io.leavesfly.jharness2.core.spi;

import java.time.Instant;
import java.util.List;

/**
 * 工具执行审计日志 SPI。
 * <p>
 * 记录 Agent 每次工具调用的输入/输出/结果,
 * 用于安全审计、故障排查、行为分析。
 */
public interface ToolAuditLogger {

    /** 记录一次工具执行 */
    void log(ToolAuditEntry entry);

    /** 按用户+时间范围查询审计日志 */
    List<ToolAuditEntry> query(String userId, Instant from, Instant to, int limit);

    /** 按会话查询审计日志 */
    List<ToolAuditEntry> queryBySession(String userId, String sessionId);
}

package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.core.spi.ToolAuditEntry;
import io.leavesfly.jharness2.core.spi.ToolAuditLogger;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 审计日志查询管理接口 —— 提供工具执行审计记录的查询能力。
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditController {

    private final ToolAuditLogger auditLogger;

    public AdminAuditController(ToolAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * 查询当前用户最近的审计日志（默认最近7天，最多100条）。
     */
    @GetMapping("/recent")
    public ResponseEntity<?> recentAuditLogs(Authentication auth,
                                              @RequestParam(defaultValue = "7") int days,
                                              @RequestParam(defaultValue = "100") int limit) {
        String userId = auth.getName();
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        Instant to = Instant.now();
        List<ToolAuditEntry> entries = auditLogger.query(userId, from, to, Math.min(limit, 500));
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "count", entries.size(),
                "entries", entries
        ));
    }

    /**
     * 查询指定会话的审计日志。
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<?> sessionAuditLogs(Authentication auth,
                                               @PathVariable String sessionId) {
        String userId = auth.getName();
        List<ToolAuditEntry> entries = auditLogger.queryBySession(userId, sessionId);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "sessionId", sessionId,
                "count", entries.size(),
                "entries", entries
        ));
    }
}

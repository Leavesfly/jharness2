package io.leavesfly.jharness2.storage.impl;

import io.leavesfly.jharness2.core.spi.ToolAuditEntry;
import io.leavesfly.jharness2.core.spi.ToolAuditLogger;
import io.leavesfly.jharness2.storage.entity.ToolAuditLogEntity;
import io.leavesfly.jharness2.storage.repository.ToolAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JpaToolAuditLogger implements ToolAuditLogger {

    private static final Logger logger = LoggerFactory.getLogger(JpaToolAuditLogger.class);
    private final ToolAuditLogRepository repository;

    public JpaToolAuditLogger(ToolAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void log(ToolAuditEntry entry) {
        ToolAuditLogEntity entity = new ToolAuditLogEntity();
        entity.setId(entry.id());
        entity.setUserId(entry.userId());
        entity.setSessionId(entry.sessionId());
        entity.setToolName(entry.toolName());
        entity.setArguments(truncate(entry.arguments(), 4096));
        entity.setResult(truncate(entry.result(), 4096));
        entity.setStatus(entry.status());
        entity.setRiskLevel(entry.riskLevel());
        entity.setDurationMs(entry.durationMs());
        entity.setExecutedAt(entry.executedAt());
        repository.save(entity);
        logger.debug("Audit logged: user={}, tool={}, status={}",
                entry.userId(), entry.toolName(), entry.status());
    }

    @Override
    public List<ToolAuditEntry> query(String userId, Instant from, Instant to, int limit) {
        return repository.findByUserIdAndTimeRange(userId, from, to, limit).stream()
                .map(this::toAuditEntry)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolAuditEntry> queryBySession(String userId, String sessionId) {
        return repository.findByUserIdAndSessionId(userId, sessionId).stream()
                .map(this::toAuditEntry)
                .collect(Collectors.toList());
    }

    private ToolAuditEntry toAuditEntry(ToolAuditLogEntity entity) {
        return new ToolAuditEntry(
                entity.getId(),
                entity.getUserId(),
                entity.getSessionId(),
                entity.getToolName(),
                entity.getArguments(),
                entity.getResult(),
                entity.getStatus(),
                entity.getRiskLevel(),
                entity.getDurationMs(),
                entity.getExecutedAt()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}

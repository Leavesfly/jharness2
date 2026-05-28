package io.leavesfly.jharness2.storage.impl;

import io.leavesfly.jharness2.core.quota.UsageRecord;
import io.leavesfly.jharness2.core.spi.ModelUsageSummary;
import io.leavesfly.jharness2.core.spi.UsageStore;
import io.leavesfly.jharness2.storage.entity.UsageRecordEntity;
import io.leavesfly.jharness2.storage.repository.UsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JpaUsageStore implements UsageStore {

    private static final Logger logger = LoggerFactory.getLogger(JpaUsageStore.class);
    private final UsageRecordRepository repository;

    public JpaUsageStore(UsageRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(UsageRecord record) {
        UsageRecordEntity entity = new UsageRecordEntity();
        entity.setUserId(record.getUserId());
        entity.setSessionId(record.getSessionId());
        entity.setModel(record.getModel());
        entity.setInputTokens(record.getInputTokens());
        entity.setOutputTokens(record.getOutputTokens());
        entity.setRecordedAt(record.getTimestamp());
        repository.save(entity);
        logger.debug("Usage recorded: user={}, model={}, tokens={}",
                record.getUserId(), record.getModel(), record.getTotalTokens());
    }

    @Override
    public long getDailyTokens(String userId, LocalDate date) {
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return repository.sumTokensByUserIdAndDay(userId, dayStart, dayEnd);
    }

    @Override
    public long getMonthlyTokens(String userId, int year, int month) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.plusMonths(1);
        Instant from = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = monthEnd.atStartOfDay(ZoneOffset.UTC).toInstant();
        return repository.sumTokensByUserIdAndMonth(userId, from, to);
    }

    @Override
    public List<UsageRecord> queryRecords(String userId, LocalDate from, LocalDate to) {
        Instant fromTime = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTime = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return repository.findByUserIdAndDateRange(userId, fromTime, toTime).stream()
                .map(this::toUsageRecord)
                .collect(Collectors.toList());
    }

    @Override
    public List<ModelUsageSummary> getModelUsageSummary(String userId, LocalDate from, LocalDate to) {
        Instant fromTime = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTime = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return repository.findModelUsageSummary(userId, fromTime, toTime).stream()
                .map(row -> new ModelUsageSummary(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).intValue()
                ))
                .collect(Collectors.toList());
    }

    private UsageRecord toUsageRecord(UsageRecordEntity entity) {
        return new UsageRecord(
                entity.getUserId(),
                entity.getSessionId(),
                entity.getModel(),
                entity.getInputTokens(),
                entity.getOutputTokens()
        );
    }
}

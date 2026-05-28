package io.leavesfly.jharness2.storage.lifecycle;

import io.leavesfly.jharness2.storage.repository.ToolAuditLogRepository;
import io.leavesfly.jharness2.storage.repository.UsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 数据生命周期管理定时任务。
 * <p>
 * 定期清理过期的审计日志、用量记录等，避免数据无限增长。
 * 配置项通过 application.yml 注入。
 */
@Component
public class DataLifecycleScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DataLifecycleScheduler.class);

    private final ToolAuditLogRepository auditLogRepository;
    private final UsageRecordRepository usageRecordRepository;

    @Value("${jharness2.storage.lifecycle.audit-log-retain-days:180}")
    private int auditLogRetainDays;

    @Value("${jharness2.storage.lifecycle.usage-detail-retain-days:365}")
    private int usageDetailRetainDays;

    public DataLifecycleScheduler(ToolAuditLogRepository auditLogRepository,
                                   UsageRecordRepository usageRecordRepository) {
        this.auditLogRepository = auditLogRepository;
        this.usageRecordRepository = usageRecordRepository;
    }

    /**
     * 每天凌晨3点执行数据清理。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void executeLifecyclePolicies() {
        logger.info("Starting data lifecycle cleanup...");
        long auditDeleted = purgeExpiredAuditLogs();
        long usageDeleted = purgeExpiredUsageRecords();
        logger.info("Data lifecycle cleanup completed. auditDeleted={}, usageDeleted={}", auditDeleted, usageDeleted);
    }

    /**
     * 清理过期的审计日志。
     */
    private long purgeExpiredAuditLogs() {
        Instant cutoff = Instant.now().minus(auditLogRetainDays, ChronoUnit.DAYS);
        try {
            long count = auditLogRepository.countByExecutedAtBefore(cutoff);
            if (count > 0) {
                auditLogRepository.deleteByExecutedAtBefore(cutoff);
                logger.info("Purged {} expired audit logs (older than {} days)", count, auditLogRetainDays);
            }
            return count;
        } catch (Exception e) {
            logger.error("Failed to purge expired audit logs: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 清理过期的用量明细。
     */
    private long purgeExpiredUsageRecords() {
        Instant cutoff = Instant.now().minus(usageDetailRetainDays, ChronoUnit.DAYS);
        try {
            long count = usageRecordRepository.countByRecordedAtBefore(cutoff);
            if (count > 0) {
                usageRecordRepository.deleteByRecordedAtBefore(cutoff);
                logger.info("Purged {} expired usage records (older than {} days)", count, usageDetailRetainDays);
            }
            return count;
        } catch (Exception e) {
            logger.error("Failed to purge expired usage records: {}", e.getMessage(), e);
            return 0;
        }
    }
}

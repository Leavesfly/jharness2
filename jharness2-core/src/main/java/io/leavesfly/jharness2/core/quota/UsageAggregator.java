package io.leavesfly.jharness2.core.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用量统计聚合器 —— 按用户/日期维度统计 token 消耗。
 * <p>
 * 提供实时的用量查询能力，供 QuotaPolicy 做超限判断。
 * 本地实现基于内存，分布式场景可扩展为 Redis 实现。
 */
public class UsageAggregator {

    private static final Logger logger = LoggerFactory.getLogger(UsageAggregator.class);

    /** key: userId:date, value: totalTokens */
    private final ConcurrentHashMap<String, AtomicLong> dailyUsage = new ConcurrentHashMap<>();

    /** key: userId, value: allTimeTokens */
    private final ConcurrentHashMap<String, AtomicLong> totalUsage = new ConcurrentHashMap<>();

    /** 可选的持久化存储，为 null 时仅使用内存统计 */
    private io.leavesfly.jharness2.core.spi.UsageStore usageStore;

    /**
     * 设置用量持久化存储。设置后每次 record 都会同步写入持久层。
     */
    public void setUsageStore(io.leavesfly.jharness2.core.spi.UsageStore usageStore) {
        this.usageStore = usageStore;
    }

    /**
     * 记录一次用量。
     */
    public void record(UsageRecord record) {
        String dailyKey = record.getUserId() + ":" + record.getDate();
        dailyUsage.computeIfAbsent(dailyKey, k -> new AtomicLong(0))
                .addAndGet(record.getTotalTokens());

        totalUsage.computeIfAbsent(record.getUserId(), k -> new AtomicLong(0))
                .addAndGet(record.getTotalTokens());

        // 持久化到存储层（如果已配置）
        if (usageStore != null) {
            try {
                usageStore.record(record);
            } catch (Exception e) {
                logger.warn("Failed to persist usage record for user={}: {}",
                        record.getUserId(), e.getMessage());
            }
        }

        logger.debug("Usage recorded: user={}, tokens={}, dailyTotal={}",
                record.getUserId(), record.getTotalTokens(), getDailyUsage(record.getUserId()));
    }

    /**
     * 获取用户今日总用量。
     */
    public long getDailyUsage(String userId) {
        String key = userId + ":" + LocalDate.now();
        AtomicLong usage = dailyUsage.get(key);
        return usage != null ? usage.get() : 0;
    }

    /**
     * 获取用户指定日期的用量。
     */
    public long getDailyUsage(String userId, LocalDate date) {
        String key = userId + ":" + date;
        AtomicLong usage = dailyUsage.get(key);
        return usage != null ? usage.get() : 0;
    }

    /**
     * 获取用户累计总用量。
     */
    public long getTotalUsage(String userId) {
        AtomicLong usage = totalUsage.get(userId);
        return usage != null ? usage.get() : 0;
    }

    /**
     * 清除过期的每日统计数据（可由定时任务调用）。
     */
    public void cleanupBefore(LocalDate cutoffDate) {
        dailyUsage.entrySet().removeIf(entry -> {
            String dateStr = entry.getKey().substring(entry.getKey().lastIndexOf(':') + 1);
            try {
                LocalDate entryDate = LocalDate.parse(dateStr);
                return entryDate.isBefore(cutoffDate);
            } catch (Exception e) {
                return false;
            }
        });
    }
}

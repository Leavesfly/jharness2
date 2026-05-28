package io.leavesfly.jharness2.core.spi;

import io.leavesfly.jharness2.core.quota.UsageRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * 用量数据持久化 SPI。
 * <p>
 * 支持写入用量记录和按维度聚合查询,供 QuotaPolicy 做超限判断,
 * 供管理接口做用量报表。
 */
public interface UsageStore {

    /** 写入一条用量记录 */
    void record(UsageRecord record);

    /** 查询用户指定日期的总 token 用量 */
    long getDailyTokens(String userId, LocalDate date);

    /** 查询用户指定月份的总 token 用量 */
    long getMonthlyTokens(String userId, int year, int month);

    /** 查询用户指定日期范围内的用量明细 */
    List<UsageRecord> queryRecords(String userId, LocalDate from, LocalDate to);

    /** 按模型维度聚合统计 */
    List<ModelUsageSummary> getModelUsageSummary(String userId, LocalDate from, LocalDate to);
}

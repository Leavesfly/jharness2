package io.leavesfly.jharness2.core.quota;

import io.leavesfly.jharness2.core.spi.ModelUsageSummary;
import io.leavesfly.jharness2.core.spi.UsageStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用量记账回归测试。
 * <p>
 * 覆盖此前"记账链路未接通导致 token 配额永不触发"的问题：
 * record 必须同时更新内存与持久层，且重启（内存清空）后要能从持久层回填。
 */
class UsageAggregatorTest {

    /** 简易内存 UsageStore，用于验证聚合器与持久层的交互 */
    private static class FakeUsageStore implements UsageStore {
        private final List<UsageRecord> records = new ArrayList<>();
        private long presetDailyTokens = 0;

        @Override
        public void record(UsageRecord record) {
            records.add(record);
        }

        @Override
        public long getDailyTokens(String userId, LocalDate date) {
            long fromRecords = records.stream()
                    .filter(r -> r.getUserId().equals(userId) && r.getDate().equals(date))
                    .mapToLong(UsageRecord::getTotalTokens)
                    .sum();
            return presetDailyTokens + fromRecords;
        }

        @Override
        public long getMonthlyTokens(String userId, int year, int month) {
            return 0;
        }

        @Override
        public List<UsageRecord> queryRecords(String userId, LocalDate from, LocalDate to) {
            return List.copyOf(records);
        }

        @Override
        public List<ModelUsageSummary> getModelUsageSummary(String userId, LocalDate from, LocalDate to) {
            return List.of();
        }
    }

    @Test
    void recordShouldUpdateDailyAndTotalUsage() {
        UsageAggregator aggregator = new UsageAggregator();
        aggregator.record(new UsageRecord("alice", "s1", "gpt", 100, 50));
        aggregator.record(new UsageRecord("alice", "s2", "gpt", 10, 5));

        assertEquals(165, aggregator.getDailyUsage("alice"));
        assertEquals(165, aggregator.getTotalUsage("alice"));
        assertEquals(0, aggregator.getDailyUsage("bob"));
    }

    @Test
    void recordShouldPersistToStore() {
        FakeUsageStore store = new FakeUsageStore();
        UsageAggregator aggregator = new UsageAggregator();
        aggregator.setUsageStore(store);

        aggregator.record(new UsageRecord("alice", "s1", "gpt", 100, 20));

        assertEquals(1, store.records.size());
        assertEquals(120, store.records.get(0).getTotalTokens());
    }

    @Test
    void zeroTokenRecordShouldBeIgnored() {
        FakeUsageStore store = new FakeUsageStore();
        UsageAggregator aggregator = new UsageAggregator();
        aggregator.setUsageStore(store);

        aggregator.record(new UsageRecord("alice", "s1", "gpt", 0, 0));

        assertEquals(0, aggregator.getDailyUsage("alice"));
        assertTrue(store.records.isEmpty());
    }

    /**
     * 重启后内存为空，必须从持久层读回当日用量，否则配额会被"清零"。
     */
    @Test
    void dailyUsageShouldFallBackToStoreAfterRestart() {
        FakeUsageStore store = new FakeUsageStore();
        store.presetDailyTokens = 900_000;

        UsageAggregator aggregator = new UsageAggregator();
        aggregator.setUsageStore(store);

        assertEquals(900_000, aggregator.getDailyUsage("alice"));
    }

    @Test
    void newRecordShouldAccumulateOnTopOfPersistedBaseline() {
        FakeUsageStore store = new FakeUsageStore();
        store.presetDailyTokens = 1000;

        UsageAggregator aggregator = new UsageAggregator();
        aggregator.setUsageStore(store);
        aggregator.record(new UsageRecord("alice", "s1", "gpt", 30, 20));

        assertEquals(1050, aggregator.getDailyUsage("alice"));
    }

    /**
     * 与配额策略联动：累计用量达到上限后必须判定为拒绝。
     */
    @Test
    void quotaPolicyShouldDenyWhenDailyUsageExceedsLimit() {
        UsageAggregator aggregator = new UsageAggregator();
        QuotaLimit limit = QuotaLimit.defaultLimit();
        QuotaPolicy policy = new QuotaPolicy() {
            @Override
            public QuotaLimit getQuotaLimit(String userId) { return limit; }

            @Override
            public QuotaCheckResult checkEngineCreation(String userId, int currentEngineCount) {
                return QuotaCheckResult.allow();
            }

            @Override
            public QuotaCheckResult checkTokenUsage(String userId, long totalTokens) {
                return totalTokens >= limit.getMaxTokensPerDay()
                        ? QuotaCheckResult.deny("over limit") : QuotaCheckResult.allow();
            }
        };

        aggregator.record(new UsageRecord("alice", "s1", "gpt",
                limit.getMaxTokensPerDay(), 0));

        assertTrue(policy.checkTokenUsage("alice", aggregator.getDailyUsage("alice")).isDenied());
        assertTrue(policy.checkTokenUsage("bob", aggregator.getDailyUsage("bob")).isAllowed());
    }

    @Test
    void cleanupShouldRemoveOldDailyEntries() {
        UsageAggregator aggregator = new UsageAggregator();
        aggregator.record(new UsageRecord("alice", "s1", "gpt", 10, 10));

        aggregator.cleanupBefore(LocalDate.now().plusDays(1));

        assertEquals(0, aggregator.getDailyUsage("alice"));
    }
}

package io.leavesfly.jharness2.core.quota;

import io.leavesfly.jharness2.core.EngineConfig;

/**
 * 默认配额策略 —— 基于 EngineConfig 中的全局配置，所有用户统一限额。
 */
public class DefaultQuotaPolicy implements QuotaPolicy {

    private final EngineConfig engineConfig;

    public DefaultQuotaPolicy(EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @Override
    public QuotaLimit getQuotaLimit(String userId) {
        return new QuotaLimit(
                engineConfig.getMaxEnginesPerUser(),
                1_000_000L,
                3,
                30
        );
    }

    @Override
    public QuotaCheckResult checkEngineCreation(String userId, int currentEngineCount) {
        int maxEngines = engineConfig.getMaxEnginesPerUser();
        if (currentEngineCount >= maxEngines) {
            return QuotaCheckResult.deny(
                    "User " + userId + " has reached max engine limit: " + maxEngines);
        }
        return QuotaCheckResult.allow();
    }

    @Override
    public QuotaCheckResult checkTokenUsage(String userId, long totalTokens) {
        QuotaLimit limit = getQuotaLimit(userId);
        if (totalTokens >= limit.getMaxTokensPerDay()) {
            return QuotaCheckResult.deny(
                    "User " + userId + " has exceeded daily token limit: " + limit.getMaxTokensPerDay());
        }
        return QuotaCheckResult.allow();
    }
}

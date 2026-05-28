package io.leavesfly.jharness2.core.pipeline;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.UserEngineRegistry;
import io.leavesfly.jharness2.core.quota.QuotaCheckResult;
import io.leavesfly.jharness2.core.quota.QuotaExceededException;
import io.leavesfly.jharness2.core.quota.QuotaPolicy;
import io.leavesfly.jharness2.core.quota.UsageAggregator;

public class QuotaInterceptor implements ChatInterceptor {
    private final QuotaPolicy quotaPolicy;
    private final UserEngineRegistry engineRegistry;
    private final UsageAggregator usageAggregator;

    public QuotaInterceptor(QuotaPolicy quotaPolicy, UserEngineRegistry engineRegistry, UsageAggregator usageAggregator) {
        this.quotaPolicy = quotaPolicy;
        this.engineRegistry = engineRegistry;
        this.usageAggregator = usageAggregator;
    }

    @Override
    public void beforeChat(UserContext context, String message) {
        long dailyUsage = usageAggregator.getDailyUsage(context.getUserId());
        QuotaCheckResult tokenResult = quotaPolicy.checkTokenUsage(context.getUserId(), dailyUsage);
        if (tokenResult.isDenied()) {
            throw new QuotaExceededException(tokenResult.getReason(), context.getUserId(), "token");
        }
    }

    @Override
    public int getOrder() { return 100; }
}

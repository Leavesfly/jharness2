package io.leavesfly.jharness2.core.config;

import io.leavesfly.jharness2.core.EngineConfig;
import io.leavesfly.jharness2.core.quota.DefaultQuotaPolicy;
import io.leavesfly.jharness2.core.quota.QuotaPolicy;
import io.leavesfly.jharness2.core.quota.UsageAggregator;
import io.leavesfly.jharness2.core.ratelimit.RateLimiter;
import io.leavesfly.jharness2.core.ratelimit.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配额与限流子域自动配置。
 */
@Configuration
public class QuotaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(QuotaPolicy.class)
    public QuotaPolicy defaultQuotaPolicy(EngineConfig engineConfig) {
        return new DefaultQuotaPolicy(engineConfig);
    }

    /**
     * 用量聚合器。存在 UsageStore 时自动挂接持久层，
     * 使记账可落库、重启后配额不会被清零。
     */
    @Bean
    @ConditionalOnMissingBean(UsageAggregator.class)
    public UsageAggregator usageAggregator(
            @Autowired(required = false) io.leavesfly.jharness2.core.spi.UsageStore usageStore) {
        UsageAggregator aggregator = new UsageAggregator();
        if (usageStore != null) {
            aggregator.setUsageStore(usageStore);
        }
        return aggregator;
    }

    /**
     * 每用户并发请求限制器（与 QuotaLimit.maxConcurrentRequests 配合）。
     */
    @Bean
    @ConditionalOnMissingBean(io.leavesfly.jharness2.core.quota.ConcurrencyLimiter.class)
    public io.leavesfly.jharness2.core.quota.ConcurrencyLimiter concurrencyLimiter() {
        return new io.leavesfly.jharness2.core.quota.ConcurrencyLimiter();
    }

    /**
     * 默认启用每用户请求限流（保守值），可通过 jharness2.ratelimit.enabled=false 关闭。
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnProperty(prefix = "jharness2.ratelimit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RateLimiter slidingWindowRateLimiter(
            @Value("${jharness2.ratelimit.requests-per-minute:30}") int requestsPerMinute) {
        return new SlidingWindowRateLimiter(requestsPerMinute, 60_000L);
    }
}

package io.leavesfly.jharness2.core.config;

import io.leavesfly.jharness2.core.EngineConfig;
import io.leavesfly.jharness2.core.quota.DefaultQuotaPolicy;
import io.leavesfly.jharness2.core.quota.QuotaPolicy;
import io.leavesfly.jharness2.core.quota.UsageAggregator;
import io.leavesfly.jharness2.core.ratelimit.RateLimiter;
import io.leavesfly.jharness2.core.ratelimit.SlidingWindowRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    @Bean
    @ConditionalOnMissingBean(UsageAggregator.class)
    public UsageAggregator usageAggregator() {
        return new UsageAggregator();
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter slidingWindowRateLimiter() {
        return new SlidingWindowRateLimiter(30, 60_000L);
    }
}

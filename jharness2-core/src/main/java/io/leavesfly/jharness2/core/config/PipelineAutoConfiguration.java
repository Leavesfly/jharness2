package io.leavesfly.jharness2.core.config;

import io.leavesfly.jharness2.core.UserEngineRegistry;
import io.leavesfly.jharness2.core.event.EngineEventBus;
import io.leavesfly.jharness2.core.metrics.EngineMetrics;
import io.leavesfly.jharness2.core.pipeline.*;
import io.leavesfly.jharness2.core.quota.QuotaPolicy;
import io.leavesfly.jharness2.core.quota.UsageAggregator;
import io.leavesfly.jharness2.core.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 请求管道自动配置 —— 注册拦截器并构建拦截器链。
 */
@Configuration
public class PipelineAutoConfiguration {

    @Bean
    @ConditionalOnBean(RateLimiter.class)
    public RateLimitInterceptor rateLimitInterceptor(RateLimiter rateLimiter) {
        return new RateLimitInterceptor(rateLimiter);
    }

    @Bean
    @ConditionalOnBean({QuotaPolicy.class, UsageAggregator.class})
    public QuotaInterceptor quotaInterceptor(QuotaPolicy quotaPolicy,
                                             UserEngineRegistry engineRegistry,
                                             UsageAggregator usageAggregator) {
        return new QuotaInterceptor(quotaPolicy, engineRegistry, usageAggregator);
    }

    @Bean
    @ConditionalOnBean(EngineMetrics.class)
    public MetricsInterceptor metricsInterceptor(EngineMetrics metrics) {
        return new MetricsInterceptor(metrics);
    }

    @Bean
    @ConditionalOnBean(EngineEventBus.class)
    public EventPublishInterceptor eventPublishInterceptor(EngineEventBus eventBus) {
        return new EventPublishInterceptor(eventBus);
    }

    @Bean
    @ConditionalOnMissingBean(ChatInterceptorChain.class)
    public ChatInterceptorChain chatInterceptorChain(
            @Autowired(required = false) List<ChatInterceptor> interceptors) {
        if (interceptors == null || interceptors.isEmpty()) {
            return new ChatInterceptorChain();
        }
        return new ChatInterceptorChain(interceptors);
    }
}

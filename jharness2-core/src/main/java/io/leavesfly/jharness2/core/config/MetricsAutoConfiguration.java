package io.leavesfly.jharness2.core.config;

import io.leavesfly.jharness2.core.UserEngineRegistry;
import io.leavesfly.jharness2.core.metrics.EngineMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics 子域自动配置 —— 仅在 MeterRegistry 可用时生效。
 */
@Configuration
@ConditionalOnBean(MeterRegistry.class)
public class MetricsAutoConfiguration {

    @Bean
    public EngineMetrics engineMetrics(MeterRegistry registry, UserEngineRegistry engineRegistry) {
        return new EngineMetrics(registry, engineRegistry);
    }
}

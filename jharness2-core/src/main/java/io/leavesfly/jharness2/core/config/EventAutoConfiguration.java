package io.leavesfly.jharness2.core.config;

import io.leavesfly.jharness2.core.event.EngineEventBus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 事件总线子域自动配置。
 */
@Configuration
public class EventAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EngineEventBus.class)
    public EngineEventBus engineEventBus() {
        return new EngineEventBus();
    }
}

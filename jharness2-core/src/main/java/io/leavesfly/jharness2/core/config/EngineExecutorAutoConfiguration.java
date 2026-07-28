package io.leavesfly.jharness2.core.config;

import io.leavesfly.jharness2.core.engine.EngineExecutors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 引擎线程池自动配置 —— 注册 Agent 循环与工具执行的有界线程池。
 */
@Configuration
@EnableConfigurationProperties(EngineExecutorConfig.class)
public class EngineExecutorAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(EngineExecutors.class)
    public EngineExecutors engineExecutors(EngineExecutorConfig config) {
        return new EngineExecutors(
                config.getAgentPoolSize(), config.getAgentQueueCapacity(),
                config.getToolPoolSize(), config.getToolQueueCapacity());
    }
}

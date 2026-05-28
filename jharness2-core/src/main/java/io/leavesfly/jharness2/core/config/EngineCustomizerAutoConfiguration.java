package io.leavesfly.jharness2.core.config;

import io.leavesfly.jharness2.core.EngineConfig;
import io.leavesfly.jharness2.core.engine.*;
import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import io.leavesfly.jharness2.core.spi.TaskStateStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 引擎定制器自动配置 —— 注册各子系统的 EngineCustomizer 实现为 Bean。
 */
@Configuration
public class EngineCustomizerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PermissionCustomizer.class)
    public PermissionCustomizer permissionCustomizer(EngineConfig engineConfig) {
        return new PermissionCustomizer(engineConfig);
    }

    @Bean
    @ConditionalOnMissingBean(CompactionCustomizer.class)
    public CompactionCustomizer compactionCustomizer() {
        return new CompactionCustomizer();
    }

    @Bean
    @ConditionalOnMissingBean(PluginCustomizer.class)
    public PluginCustomizer pluginCustomizer() {
        return new PluginCustomizer();
    }

    @Bean
    @ConditionalOnMissingBean(AgentCustomizer.class)
    public AgentCustomizer agentCustomizer(@Autowired(required = false) TaskStateStore taskStateStore) {
        AgentCustomizer customizer = new AgentCustomizer();
        if (taskStateStore != null) {
            customizer.setTaskStateStore(taskStateStore);
        }
        return customizer;
    }

    @Bean
    @ConditionalOnMissingBean(SessionPersistCustomizer.class)
    public SessionPersistCustomizer sessionPersistCustomizer(SessionPersistenceService sessionStorageService) {
        return new SessionPersistCustomizer(sessionStorageService);
    }
}

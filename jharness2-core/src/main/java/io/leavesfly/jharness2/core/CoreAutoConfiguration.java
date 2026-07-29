package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.core.config.EngineExecutorConfig;
import io.leavesfly.jharness2.core.engine.AgentCustomizer;
import io.leavesfly.jharness2.core.engine.CompactionCustomizer;
import io.leavesfly.jharness2.core.engine.EngineExecutors;
import io.leavesfly.jharness2.core.engine.PermissionCustomizer;
import io.leavesfly.jharness2.core.engine.PluginCustomizer;
import io.leavesfly.jharness2.core.engine.SessionPersistCustomizer;
import io.leavesfly.jharness2.core.metrics.EngineMetrics;
import io.leavesfly.jharness2.core.quota.ConcurrencyLimiter;
import io.leavesfly.jharness2.core.quota.DefaultQuotaPolicy;
import io.leavesfly.jharness2.core.quota.QuotaPolicy;
import io.leavesfly.jharness2.core.quota.UsageAggregator;
import io.leavesfly.jharness2.core.ratelimit.RateLimiter;
import io.leavesfly.jharness2.core.ratelimit.SlidingWindowRateLimiter;
import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import io.leavesfly.jharness2.core.spi.TaskStateStore;
import io.leavesfly.jharness2.core.spi.UsageStore;
import io.leavesfly.jharness2.core.workspace.WorkspaceConfig;
import io.leavesfly.jharness2.core.workspace.WorkspaceTemplateService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Core 层自动配置 —— 引擎定制器、线程池、配额与限流、Workspace、Metrics。
 * <p>
 * Checkpoint 子域的配置由 storage 模块的 {@code CheckpointAutoConfiguration} 提供，
 * 引擎注册表的配置见 {@code EngineRegistryAutoConfiguration}。
 */
@Configuration
@EnableConfigurationProperties(EngineExecutorConfig.class)
public class CoreAutoConfiguration {

    // ---------- 引擎线程池 ----------

    /**
     * Agent 循环与工具执行的有界线程池，
     * 避免阻塞式工作负载占满 ForkJoinPool.commonPool 导致全服务饿死。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(EngineExecutors.class)
    public EngineExecutors engineExecutors(EngineExecutorConfig config) {
        return new EngineExecutors(
                config.getAgentPoolSize(), config.getAgentQueueCapacity(),
                config.getToolPoolSize(), config.getToolQueueCapacity());
    }

    // ---------- 引擎定制器 ----------

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

    // ---------- 配额与限流 ----------

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
    public UsageAggregator usageAggregator(@Autowired(required = false) UsageStore usageStore) {
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
    @ConditionalOnMissingBean(ConcurrencyLimiter.class)
    public ConcurrencyLimiter concurrencyLimiter() {
        return new ConcurrencyLimiter();
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

    // ---------- Workspace ----------

    @Bean
    @ConditionalOnProperty(prefix = "jharness2.workspace", name = "template-directory")
    public WorkspaceTemplateService workspaceTemplateService(WorkspaceConfig workspaceConfig) {
        return new WorkspaceTemplateService(Path.of(workspaceConfig.getTemplateDirectory()));
    }

    // ---------- Metrics ----------

    /**
     * Metrics 埋点 —— 仅在 micrometer 在类路径上时生效（web 模块经 actuator 引入）。
     * <p>
     * 注意不能用 @ConditionalOnBean(MeterRegistry.class)：本类是组件扫描的普通配置，
     * 条件评估早于 actuator 自动配置注册 MeterRegistry，会导致条件恒不成立。
     */
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(EngineMetrics.class)
        public EngineMetrics engineMetrics(MeterRegistry registry, UserEngineRegistry engineRegistry) {
            return new EngineMetrics(registry, engineRegistry);
        }
    }
}

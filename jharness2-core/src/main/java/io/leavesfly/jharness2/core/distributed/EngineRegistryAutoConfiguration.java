package io.leavesfly.jharness2.core.distributed;

import io.leavesfly.jharness2.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 引擎注册表自动配置。
 * <p>
 * - 当 jharness2.engine.distributed.enabled=true 且 Redis 可用时，使用分布式注册表
 * - 否则使用本地 Caffeine 注册表（默认）
 */
@Configuration
@EnableScheduling
public class EngineRegistryAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EngineRegistryAutoConfiguration.class);

    /**
     * 分布式模式：Redis 状态存储。
     */
    @Bean
    @ConditionalOnProperty(prefix = "jharness2.engine.distributed", name = "enabled", havingValue = "true")
    public EngineStateStore engineStateStore(StringRedisTemplate redisTemplate) {
        logger.info("Creating RedisEngineStateStore for distributed mode");
        return new RedisEngineStateStore(redisTemplate);
    }

    /**
     * 分布式模式：DistributedEngineRegistry。
     */
    @Bean
    @ConditionalOnProperty(prefix = "jharness2.engine.distributed", name = "enabled", havingValue = "true")
    public UserEngineRegistry distributedEngineRegistry(EngineFactory engineFactory,
                                                        EngineConfig engineConfig,
                                                        EngineStateStore stateStore) {
        logger.info("Using DistributedEngineRegistry (Redis-backed)");
        return new DistributedEngineRegistry(engineFactory, engineConfig, stateStore);
    }

    /**
     * 本地模式（默认）：LocalEngineRegistry。
     */
    @Bean
    @ConditionalOnMissingBean(UserEngineRegistry.class)
    public UserEngineRegistry localEngineRegistry(EngineFactory engineFactory,
                                                   EngineConfig engineConfig) {
        logger.info("Using LocalEngineRegistry (Caffeine-only, single node)");
        return new LocalEngineRegistry(engineFactory, engineConfig);
    }

    /**
     * 分布式模式下的状态同步调度器。
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jharness2.engine.distributed", name = "enabled", havingValue = "true")
    static class StateSyncScheduler {

        private final UserEngineRegistry registry;
        private final EngineConfig engineConfig;

        StateSyncScheduler(UserEngineRegistry registry, EngineConfig engineConfig) {
            this.registry = registry;
            this.engineConfig = engineConfig;
        }

        @Scheduled(fixedDelayString = "${jharness2.engine.distributed.state-sync-interval-seconds:10}000")
        public void syncStates() {
            if (registry instanceof DistributedEngineRegistry distributed) {
                distributed.syncAllStates();
            }
        }
    }
}

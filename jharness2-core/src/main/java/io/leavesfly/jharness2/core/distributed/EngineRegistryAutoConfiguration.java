package io.leavesfly.jharness2.core.distributed;

import io.leavesfly.jharness2.core.*;
import io.leavesfly.jharness2.core.checkpoint.SessionCheckpointService;
import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 引擎注册表自动配置。
 * <p>
 * - 当 jharness2.engine.distributed.enabled=true 且 EngineStateStore 可用时，使用分布式注册表
 * - 否则使用本地 Caffeine 注册表（默认）
 * <p>
 * 注意：EngineStateStore / NodeLoadStore 的 Bean 注册由 storage 模块的
 * {@code DistributedStoreAutoConfiguration} 提供。
 */
@Configuration
@EnableScheduling
public class EngineRegistryAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EngineRegistryAutoConfiguration.class);

    /**
     * 分布式模式：DistributedEngineRegistry。
     * 依赖 storage 模块提供的 EngineStateStore Bean。
     */
    @Bean
    @ConditionalOnBean(EngineStateStore.class)
    @ConditionalOnProperty(prefix = "jharness2.engine.distributed", name = "enabled", havingValue = "true")
    public UserEngineRegistry distributedEngineRegistry(EngineFactory engineFactory,
                                                        EngineConfig engineConfig,
                                                        EngineStateStore stateStore) {
        logger.info("Using DistributedEngineRegistry (Redis-backed)");
        return new DistributedEngineRegistry(engineFactory, engineConfig, stateStore);
    }

    /**
     * 本地模式（默认）：LocalEngineRegistry。
     * 可选注入 SessionPersistenceService，缓存未命中时从持久化存储恢复会话历史；
     * 可选注入 SessionCheckpointService，DB 快照不可用时从 checkpoint 兑底恢复。
     */
    @Bean
    @ConditionalOnMissingBean(UserEngineRegistry.class)
    public UserEngineRegistry localEngineRegistry(EngineFactory engineFactory,
                                                   EngineConfig engineConfig,
                                                   @Autowired(required = false) SessionPersistenceService sessionPersistence,
                                                   @Autowired(required = false) SessionCheckpointService checkpointService) {
        logger.info("Using LocalEngineRegistry (Caffeine-only, single node, sessionRestore={}, checkpointFallback={})",
                sessionPersistence != null, checkpointService != null);
        return new LocalEngineRegistry(engineFactory, engineConfig, sessionPersistence, checkpointService);
    }

    /**
     * 分布式模式下的状态同步调度器。
     * 同一心跳周期内顺带续注本节点地址（若配置了 advertise-address），
     * 供其他节点做请求转发时的属主地址发现。
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jharness2.engine.distributed", name = "enabled", havingValue = "true")
    static class StateSyncScheduler {

        /** 地址 TTL = 同步间隔 × 3，连丢两次心跳仍存活，第三次才视为下线 */
        private static final int ADDRESS_TTL_FACTOR = 3;

        private final UserEngineRegistry registry;
        private final EngineConfig engineConfig;
        private final NodeAddressRegistry nodeAddressRegistry;

        StateSyncScheduler(UserEngineRegistry registry, EngineConfig engineConfig,
                           @Autowired(required = false) NodeAddressRegistry nodeAddressRegistry) {
            this.registry = registry;
            this.engineConfig = engineConfig;
            this.nodeAddressRegistry = nodeAddressRegistry;
        }

        @Scheduled(fixedDelayString = "${jharness2.engine.distributed.state-sync-interval-seconds:10}000")
        public void syncStates() {
            if (registry instanceof DistributedEngineRegistry distributed) {
                distributed.syncAllStates();
                registerNodeAddress(distributed);
            }
        }

        private void registerNodeAddress(DistributedEngineRegistry distributed) {
            String address = engineConfig.getDistributed().getAdvertiseAddress();
            if (nodeAddressRegistry == null || address == null || address.isBlank()) {
                return;
            }
            try {
                java.time.Duration ttl = java.time.Duration.ofSeconds(
                        (long) engineConfig.getDistributed().getStateSyncIntervalSeconds() * ADDRESS_TTL_FACTOR);
                nodeAddressRegistry.register(distributed.getNodeId(), address.trim(), ttl);
            } catch (Exception e) {
                logger.warn("Failed to register node address: {}", e.getMessage());
            }
        }

        @jakarta.annotation.PreDestroy
        void unregisterNodeAddress() {
            if (nodeAddressRegistry != null && registry instanceof DistributedEngineRegistry distributed) {
                try {
                    nodeAddressRegistry.unregister(distributed.getNodeId());
                } catch (Exception e) {
                    logger.warn("Failed to unregister node address: {}", e.getMessage());
                }
            }
        }
    }
}

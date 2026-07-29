package io.leavesfly.jharness2.storage.distributed;

import io.leavesfly.jharness2.core.distributed.EngineStateStore;
import io.leavesfly.jharness2.core.distributed.NodeAddressRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 分布式存储 Bean 自动配置。
 * <p>
 * 提供 EngineStateStore（Redis）的默认实现。
 */
@Configuration
public class DistributedStoreAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DistributedStoreAutoConfiguration.class);

    /**
     * 分布式模式：Redis 引擎状态存储。
     */
    @Bean
    @ConditionalOnProperty(prefix = "jharness2.engine.distributed", name = "enabled", havingValue = "true")
    public EngineStateStore engineStateStore(StringRedisTemplate redisTemplate) {
        logger.info("Creating RedisEngineStateStore for distributed mode");
        return new RedisEngineStateStore(redisTemplate);
    }

    /**
     * 分布式模式：Redis 节点地址注册表（供节点间请求转发做属主地址发现）。
     */
    @Bean
    @ConditionalOnMissingBean(NodeAddressRegistry.class)
    @ConditionalOnProperty(prefix = "jharness2.engine.distributed", name = "enabled", havingValue = "true")
    public NodeAddressRegistry nodeAddressRegistry(StringRedisTemplate redisTemplate) {
        logger.info("Creating RedisNodeAddressRegistry for cross-node request forwarding");
        return new RedisNodeAddressRegistry(redisTemplate);
    }
}

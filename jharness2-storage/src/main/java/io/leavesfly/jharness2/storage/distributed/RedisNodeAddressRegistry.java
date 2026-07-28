package io.leavesfly.jharness2.storage.distributed;

import io.leavesfly.jharness2.core.distributed.NodeAddressRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Redis 的节点地址注册表。
 * <p>
 * Key 设计：jharness2:node:addr:{nodeId} → address（带 TTL，随心跳续期）。
 */
public class RedisNodeAddressRegistry implements NodeAddressRegistry {

    private static final String ADDR_PREFIX = "jharness2:node:addr:";

    private final StringRedisTemplate redis;

    public RedisNodeAddressRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void register(String nodeId, String address, Duration ttl) {
        redis.opsForValue().set(ADDR_PREFIX + nodeId, address, ttl);
    }

    @Override
    public Optional<String> lookup(String nodeId) {
        return Optional.ofNullable(redis.opsForValue().get(ADDR_PREFIX + nodeId));
    }

    @Override
    public void unregister(String nodeId) {
        redis.delete(ADDR_PREFIX + nodeId);
    }
}

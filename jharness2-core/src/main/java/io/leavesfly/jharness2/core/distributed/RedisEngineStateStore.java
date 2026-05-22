package io.leavesfly.jharness2.core.distributed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Redis 的引擎状态存储实现。
 * <p>
 * Key 设计：
 * - engine:state:{cacheKey}  → EngineState JSON (带 TTL)
 * - engine:owner:{cacheKey}  → nodeId (带租约 TTL)
 * - engine:count:{userId}    → 用户引擎计数
 */
public class RedisEngineStateStore implements EngineStateStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisEngineStateStore.class);

    private static final String STATE_PREFIX = "jharness2:engine:state:";
    private static final String OWNER_PREFIX = "jharness2:engine:owner:";
    private static final String COUNT_PREFIX = "jharness2:engine:count:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisEngineStateStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void save(String cacheKey, EngineState state, Duration ttl) {
        try {
            String json = mapper.writeValueAsString(state);
            redis.opsForValue().set(STATE_PREFIX + cacheKey, json, ttl);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize engine state: key={}", cacheKey, e);
        }
    }

    @Override
    public Optional<EngineState> load(String cacheKey) {
        String json = redis.opsForValue().get(STATE_PREFIX + cacheKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            EngineState state = mapper.readValue(json, EngineState.class);
            return Optional.of(state);
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize engine state: key={}", cacheKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void remove(String cacheKey) {
        redis.delete(STATE_PREFIX + cacheKey);
        redis.delete(OWNER_PREFIX + cacheKey);
    }

    @Override
    public void touch(String cacheKey, Duration ttl) {
        redis.expire(STATE_PREFIX + cacheKey, ttl);
    }

    @Override
    public int countByUser(String userId) {
        String value = redis.opsForValue().get(COUNT_PREFIX + userId);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void incrementUserCount(String userId) {
        redis.opsForValue().increment(COUNT_PREFIX + userId);
    }

    @Override
    public void decrementUserCount(String userId) {
        redis.opsForValue().decrement(COUNT_PREFIX + userId);
    }

    @Override
    public boolean tryAcquireOwnership(String cacheKey, String nodeId, Duration lease) {
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(OWNER_PREFIX + cacheKey, nodeId, lease);
        if (Boolean.TRUE.equals(acquired)) {
            return true;
        }
        // 如果已有所有权且是自己的，续租
        String currentOwner = redis.opsForValue().get(OWNER_PREFIX + cacheKey);
        if (nodeId.equals(currentOwner)) {
            redis.expire(OWNER_PREFIX + cacheKey, lease);
            return true;
        }
        return false;
    }

    @Override
    public void releaseOwnership(String cacheKey, String nodeId) {
        String currentOwner = redis.opsForValue().get(OWNER_PREFIX + cacheKey);
        if (nodeId.equals(currentOwner)) {
            redis.delete(OWNER_PREFIX + cacheKey);
        }
    }
}

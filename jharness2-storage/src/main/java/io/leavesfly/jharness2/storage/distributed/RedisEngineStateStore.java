package io.leavesfly.jharness2.storage.distributed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.leavesfly.jharness2.core.distributed.EngineState;
import io.leavesfly.jharness2.core.distributed.EngineStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

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

    /** 原子“未超限则递增”：KEYS[1]=计数 key, ARGV[1]=上限，返回 1=占位成功 */
    private static final RedisScript<Long> TRY_INCREMENT_SCRIPT = RedisScript.of(
            "local current = tonumber(redis.call('get', KEYS[1]) or '0') "
                    + "if current >= tonumber(ARGV[1]) then return 0 end "
                    + "redis.call('incr', KEYS[1]) return 1", Long.class);

    /** 递减但不低于 0，返回递减后的值 */
    private static final RedisScript<Long> DECREMENT_SCRIPT = RedisScript.of(
            "local current = tonumber(redis.call('get', KEYS[1]) or '0') "
                    + "if current <= 0 then redis.call('set', KEYS[1], '0') return 0 end "
                    + "return redis.call('decr', KEYS[1])", Long.class);

    /** 原子获取或续租所有权：KEYS[1]=owner key, ARGV[1]=nodeId, ARGV[2]=租约秒 */
    private static final RedisScript<Long> ACQUIRE_OWNERSHIP_SCRIPT = RedisScript.of(
            "local owner = redis.call('get', KEYS[1]) "
                    + "if owner and owner ~= ARGV[1] then return 0 end "
                    + "redis.call('set', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[2])) return 1", Long.class);

    /** 仅当所有者是自己时才释放 */
    private static final RedisScript<Long> RELEASE_OWNERSHIP_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0",
            Long.class);

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
        // 不让计数跌到负数：异常路径下的多次递减会把后续配额判定带偏
        Long remaining = redis.execute(DECREMENT_SCRIPT,
                java.util.List.of(COUNT_PREFIX + userId));
        if (remaining == null) {
            redis.opsForValue().decrement(COUNT_PREFIX + userId);
        }
    }

    /**
     * 原子“校验上限并递增”：避免多节点并发时同时通过校验而突破每用户引擎数上限。
     */
    @Override
    public boolean tryIncrementUserCount(String userId, int maxCount) {
        Long result = redis.execute(TRY_INCREMENT_SCRIPT,
                java.util.List.of(COUNT_PREFIX + userId), String.valueOf(maxCount));
        return result != null && result == 1L;
    }

    /**
     * 原子获取/续租所有权：“不存在则写入，已存在且属于自己则续租”必须一步完成，
     * 否则 setIfAbsent→get→expire 之间的窗口会导致所有权判定错误。
     */
    @Override
    public boolean tryAcquireOwnership(String cacheKey, String nodeId, Duration lease) {
        Long result = redis.execute(ACQUIRE_OWNERSHIP_SCRIPT,
                java.util.List.of(OWNER_PREFIX + cacheKey),
                nodeId, String.valueOf(Math.max(1, lease.toSeconds())));
        return result != null && result == 1L;
    }

    /**
     * 只删除属于自己的租约（原子 compare-and-delete），避免误删已被其他节点接管的所有权。
     */
    @Override
    public void releaseOwnership(String cacheKey, String nodeId) {
        redis.execute(RELEASE_OWNERSHIP_SCRIPT,
                java.util.List.of(OWNER_PREFIX + cacheKey), nodeId);
    }
}

package io.leavesfly.jharness2.core.distributed;

import java.time.Duration;
import java.util.Optional;

/**
 * 节点地址注册表 —— 供节点间请求转发（内置反向代理）做属主地址发现。
 * <p>
 * 各节点随状态同步心跳周期性续注自身地址（带 TTL，节点宕机后自动过期），
 * 非属主节点收到会话请求时按属主 nodeId 查询可达地址并转发。
 * 实现应基于跨节点共享的存储（如 Redis），进程内实现仅适用于测试。
 */
public interface NodeAddressRegistry {

    /**
     * 注册/续期本节点地址。
     *
     * @param nodeId  节点 ID
     * @param address 对其他节点可达的地址（如 http://10.0.0.1:8080）
     * @param ttl     存活时间，到期未续期视为节点下线
     */
    void register(String nodeId, String address, Duration ttl);

    /**
     * 查询节点地址。
     *
     * @return 节点地址；节点未注册或已过期时为 empty
     */
    Optional<String> lookup(String nodeId);

    /**
     * 注销本节点地址（优雅停机时调用）。
     */
    void unregister(String nodeId);
}

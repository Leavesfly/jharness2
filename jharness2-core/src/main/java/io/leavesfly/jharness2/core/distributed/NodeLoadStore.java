package io.leavesfly.jharness2.core.distributed;

import java.util.List;
import java.util.Optional;

/**
 * 节点负载信息存储接口 —— 独立于引擎状态存储，遵循接口隔离原则。
 */
public interface NodeLoadStore {
    void report(String nodeId, NodeLoadInfo loadInfo);
    Optional<NodeLoadInfo> getLoad(String nodeId);
    List<NodeLoadInfo> getAllNodes();
}

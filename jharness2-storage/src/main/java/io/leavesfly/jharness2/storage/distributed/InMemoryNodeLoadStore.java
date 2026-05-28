package io.leavesfly.jharness2.storage.distributed;

import io.leavesfly.jharness2.core.distributed.NodeLoadInfo;
import io.leavesfly.jharness2.core.distributed.NodeLoadStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的节点负载存储（单机/测试场景下的默认实现）。
 */
public class InMemoryNodeLoadStore implements NodeLoadStore {

    private final ConcurrentHashMap<String, NodeLoadInfo> loads = new ConcurrentHashMap<>();

    @Override
    public void report(String nodeId, NodeLoadInfo loadInfo) {
        loads.put(nodeId, loadInfo);
    }

    @Override
    public Optional<NodeLoadInfo> getLoad(String nodeId) {
        return Optional.ofNullable(loads.get(nodeId));
    }

    @Override
    public List<NodeLoadInfo> getAllNodes() {
        return new ArrayList<>(loads.values());
    }
}

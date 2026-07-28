package io.leavesfly.jharness2.core.distributed;

/**
 * 当引擎当前被另一个节点持有时抛出此异常。
 * 携带属主 nodeId，供上层（如 Web 层内置反向代理）将请求转发到属主节点；
 * 无法转发时客户端应重试或依赖会话亲和路由。
 */
public class EngineOwnedByOtherNodeException extends RuntimeException {

    /** 属主节点 ID，可能为 null（如创建期竞争抢占时无法确定属主） */
    private final String ownerNodeId;

    public EngineOwnedByOtherNodeException(String message) {
        this(message, null);
    }

    public EngineOwnedByOtherNodeException(String message, String ownerNodeId) {
        super(message);
        this.ownerNodeId = ownerNodeId;
    }

    public String getOwnerNodeId() {
        return ownerNodeId;
    }
}

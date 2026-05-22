package io.leavesfly.jharness2.core.distributed;

/**
 * 当引擎当前被另一个节点持有时抛出此异常。
 * 客户端应重试或路由到正确的节点。
 */
public class EngineOwnedByOtherNodeException extends RuntimeException {

    public EngineOwnedByOtherNodeException(String message) {
        super(message);
    }
}

package io.leavesfly.jharness2.engine;

/**
 * 引擎忙碌异常 —— 同一 session 的引擎正在处理另一条消息时抛出。
 * <p>
 * Web 层应将其映射为 409 Conflict 语义,提示用户等待当前回复完成。
 */
public class EngineBusyException extends RuntimeException {

    public EngineBusyException(String message) {
        super(message);
    }
}

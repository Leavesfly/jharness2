package io.leavesfly.jharness2.engine;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.util.List;

/**
 * 会话持久化接口 — 替代原来的 Consumer&lt;List&lt;ConversationMessage&gt;&gt;。
 * <p>
 * 支持全量和增量持久化，由实现方决定具体策略。
 * 标记为 FunctionalInterface 以便直接使用 lambda 构造简单实现。
 */
@FunctionalInterface
public interface SessionPersister {

    /**
     * 持久化当前完整消息列表（全量模式）。
     *
     * @param messages 当前完整的对话消息列表
     */
    void persist(List<ConversationMessage> messages);

    /**
     * 持久化新增的消息（增量模式）。
     * <p>
     * 默认实现退化为全量模式，子类可覆盖以实现真正的增量持久化。
     *
     * @param allMessages   当前完整的对话消息列表
     * @param newMessages   本轮新增的消息
     */
    default void persistIncremental(List<ConversationMessage> allMessages, List<ConversationMessage> newMessages) {
        persist(allMessages);
    }
}

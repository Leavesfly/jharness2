package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.util.List;

public interface EngineFactory {
    EngineInstance create(UserContext context);

    /**
     * 从已有状态恢复引擎实例（分布式场景：跨节点迁移时使用）。
     *
     * @param context  用户上下文
     * @param messages 待恢复的对话历史
     * @param inputTokens  已累计输入 token 数
     * @param outputTokens 已累计输出 token 数
     */
    EngineInstance restore(UserContext context, List<ConversationMessage> messages,
                           long inputTokens, long outputTokens);
}

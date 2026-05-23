package io.leavesfly.jharness2.engine.compaction;

import io.leavesfly.jharness2.engine.ConversationMessage;
import io.leavesfly.jharness2.engine.LlmClient;

import java.util.List;

/**
 * 消息压缩策略接口 — 支持自定义压缩算法。
 * <p>
 * 默认实现为 {@link MessageCompactionService}，基于 LLM 摘要。
 * 扩展方可提供如：基于 token 精确计数、基于向量化摘要、不压缩等策略。
 */
public interface CompactionStrategy {

    /**
     * 判断当前消息列表是否需要压缩。
     */
    boolean needsCompaction(List<ConversationMessage> messages);

    /**
     * 执行压缩，返回压缩后的消息列表。
     *
     * @param messages  当前完整消息列表
     * @param llmClient LLM 客户端（可用于生成摘要）
     * @return 压缩后的消息列表
     */
    List<ConversationMessage> compact(List<ConversationMessage> messages, LlmClient llmClient);
}

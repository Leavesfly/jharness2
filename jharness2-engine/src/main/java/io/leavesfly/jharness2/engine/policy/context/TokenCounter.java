package io.leavesfly.jharness2.engine.policy.context;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.util.List;

/**
 * Token 计数器接口 — 用于精确计算消息的 token 数量。
 * <p>
 * 不同模型有不同的 tokenizer（如 GPT-4 用 cl100k_base，Claude 用自有 tokenizer），
 * 实现方可接入具体的 tokenizer 库（如 jtokkit），或使用近似估算。
 */
public interface TokenCounter {

    /** 计算单条文本的 token 数 */
    int countTokens(String text);

    /** 计算单条消息的 token 数（含 role 标记和结构开销） */
    int countMessage(ConversationMessage message);

    /** 计算消息列表的总 token 数 */
    default int countMessages(List<ConversationMessage> messages) {
        return messages.stream().mapToInt(this::countMessage).sum();
    }

    /** 计算工具 Schema 的 token 数 */
    int countToolSchemas(List<java.util.Map<String, Object>> tools);
}

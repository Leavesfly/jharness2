package io.leavesfly.jharness2.engine.policy.context;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.util.List;
import java.util.Map;

/**
 * 基于字符/词估算的 Token 计数器 — 无需外部依赖，适用于快速开发和测试。
 * <p>
 * 估算规则：
 * <ul>
 *   <li>英文：约 4 字符 = 1 token</li>
 *   <li>中文：约 1.5 字符 = 1 token</li>
 *   <li>每条消息额外 4 token 开销（role + 结构标记）</li>
 * </ul>
 * 生产环境建议接入 jtokkit 等精确 tokenizer。
 */
public class EstimateTokenCounter implements TokenCounter {

    /** 每个字符对应的平均 token 数（混合中英文场景的经验值） */
    private static final double CHARS_PER_TOKEN = 3.0;

    /** 每条消息的固定开销 token */
    private static final int MESSAGE_OVERHEAD = 4;

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    @Override
    public int countMessage(ConversationMessage message) {
        int tokens = MESSAGE_OVERHEAD;
        if (message.getContent() != null) {
            tokens += countTokens(message.getContent());
        }
        if (message.getToolCalls() != null) {
            for (ConversationMessage.ToolCall tc : message.getToolCalls()) {
                tokens += countTokens(tc.getFunction().getName());
                tokens += countTokens(tc.getFunction().getArguments());
                tokens += 3; // tool call 结构开销
            }
        }
        return tokens;
    }

    @Override
    public int countToolSchemas(List<Map<String, Object>> tools) {
        if (tools == null || tools.isEmpty()) return 0;
        // 估算：每个工具 schema 大约占用其 JSON 字符串长度的 token
        int total = 0;
        for (Map<String, Object> tool : tools) {
            total += countTokens(tool.toString());
        }
        return total;
    }
}

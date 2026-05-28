package io.leavesfly.jharness2.engine.policy.access;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.util.List;

/**
 * Guardrail 接口 — LLM 输入/输出级安全防护。
 * <p>
 * 与 PermissionChecker（工具级权限控制）不同，Guardrail 作用于 LLM 通信的输入输出：
 * <ul>
 *   <li><b>INPUT</b>：在消息发送给 LLM 之前检查（防注入、内容审核、敏感信息过滤）</li>
 *   <li><b>OUTPUT</b>：在 LLM 回复后检查（输出格式校验、敏感信息拦截、内容合规）</li>
 * </ul>
 *
 * <p>设计参考 OpenAI Agents SDK Guardrail 和 NeMo Guardrails。
 */
public interface Guardrail {

    /** Guardrail 作用阶段 */
    enum Phase {
        /** 用户输入 → LLM 之前 */
        INPUT,
        /** LLM 输出 → 返回用户之前 */
        OUTPUT
    }

    /** 此 Guardrail 作用于哪个阶段 */
    Phase getPhase();

    /** Guardrail 名称（用于日志和追踪） */
    String getName();

    /**
     * 执行检查。
     *
     * @param messages 当前对话历史（含最新消息）
     * @param content  待检查的内容（INPUT 阶段为用户输入，OUTPUT 阶段为 LLM 输出）
     * @return 检查结果
     */
    GuardrailResult check(List<ConversationMessage> messages, String content);
}

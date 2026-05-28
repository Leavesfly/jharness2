package io.leavesfly.jharness2.engine.ext.agent;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Agent 间任务交接定义 — 主 Agent 可将控制权转交给 Sub-Agent。
 * <p>
 * 与简单的多 LLM 调用不同，Handoff 的 Sub-Agent 是一个完整的 ReAct 循环引擎，
 * 拥有独立的 tools、systemPrompt、maxTurns，可以执行工具调用。
 * <p>
 * 设计参考 OpenAI Agents SDK 的 Handoff 模式。
 *
 * <pre>
 * 主 Agent → handoff("code-reviewer") → Sub-Agent(独立 QueryEngine)
 *                                          ├─ LLM 调用
 *                                          ├─ 工具调用
 *                                          └─ 返回结果 → 主 Agent 继续
 * </pre>
 */
public class Handoff {

    private final String targetAgentName;
    private final String description;
    private final AgentRole role;
    private final InputFilter inputFilter;

    private Handoff(Builder builder) {
        this.targetAgentName = builder.targetAgentName;
        this.description = builder.description;
        this.role = builder.role;
        this.inputFilter = builder.inputFilter;
    }

    /** 目标 Agent 名称 */
    public String getTargetAgentName() { return targetAgentName; }

    /** Handoff 描述（帮助主 Agent 的 LLM 理解何时触发此 Handoff） */
    public String getDescription() { return description; }

    /** 目标 Agent 的角色定义（含 systemPrompt） */
    public AgentRole getRole() { return role; }

    /** 输入过滤器 — 控制传递给 Sub-Agent 的上下文 */
    public InputFilter getInputFilter() { return inputFilter; }

    public static Builder builder(String targetAgentName) {
        return new Builder(targetAgentName);
    }

    /**
     * 输入过滤器 — 决定哪些消息传递给 Sub-Agent。
     */
    @FunctionalInterface
    public interface InputFilter {
        /**
         * 过滤/转换传递给 Sub-Agent 的消息列表。
         *
         * @param messages 主 Agent 的当前对话历史
         * @return Sub-Agent 应接收的消息列表
         */
        List<ConversationMessage> filter(List<ConversationMessage> messages);
    }

    /** 预置过滤器：仅传递最后 N 条消息 */
    public static InputFilter lastNMessages(int n) {
        return messages -> {
            if (messages.size() <= n) return messages;
            return messages.subList(messages.size() - n, messages.size());
        };
    }

    /** 预置过滤器：仅传递用户消息和系统消息 */
    public static InputFilter userAndSystemOnly() {
        return messages -> messages.stream()
                .filter(m -> m.getRole() == ConversationMessage.Role.USER
                        || m.getRole() == ConversationMessage.Role.SYSTEM)
                .toList();
    }

    /** 预置过滤器：不传递历史（Sub-Agent 从零开始） */
    public static InputFilter noHistory() {
        return messages -> List.of();
    }

    public static class Builder {
        private final String targetAgentName;
        private String description = "";
        private AgentRole role;
        private InputFilter inputFilter;

        private Builder(String targetAgentName) {
            this.targetAgentName = targetAgentName;
        }

        public Builder description(String description) { this.description = description; return this; }
        public Builder role(AgentRole role) { this.role = role; return this; }
        public Builder inputFilter(InputFilter filter) { this.inputFilter = filter; return this; }

        public Handoff build() {
            if (role == null) {
                role = new AgentRole(targetAgentName, null);
            }
            return new Handoff(this);
        }
    }
}

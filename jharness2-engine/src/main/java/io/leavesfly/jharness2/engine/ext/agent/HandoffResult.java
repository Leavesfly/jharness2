package io.leavesfly.jharness2.engine.ext.agent;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import java.util.List;

/**
 * Handoff 执行结果 — Sub-Agent 完成任务后返回给主 Agent。
 */
public class HandoffResult {

    private final String agentName;
    private final String output;
    private final boolean success;
    private final long durationMs;
    private final int turnsUsed;
    private final List<ConversationMessage> agentMessages;

    public HandoffResult(String agentName, String output, boolean success,
                         long durationMs, int turnsUsed, List<ConversationMessage> agentMessages) {
        this.agentName = agentName;
        this.output = output;
        this.success = success;
        this.durationMs = durationMs;
        this.turnsUsed = turnsUsed;
        this.agentMessages = agentMessages != null ? List.copyOf(agentMessages) : List.of();
    }

    public String getAgentName() { return agentName; }
    public String getOutput() { return output; }
    public boolean isSuccess() { return success; }
    public long getDurationMs() { return durationMs; }
    public int getTurnsUsed() { return turnsUsed; }

    /** Sub-Agent 的完整对话历史（可用于调试/审计） */
    public List<ConversationMessage> getAgentMessages() { return agentMessages; }
}

package io.leavesfly.jharness2.core.engine.agent;

public class AgentResult {
    private final String agentName;
    private final String output;
    private final boolean success;
    private final long durationMs;

    public AgentResult(String agentName, String output, boolean success, long durationMs) {
        this.agentName = agentName;
        this.output = output;
        this.success = success;
        this.durationMs = durationMs;
    }

    public String getAgentName() { return agentName; }
    public String getOutput() { return output; }
    public boolean isSuccess() { return success; }
    public long getDurationMs() { return durationMs; }
}

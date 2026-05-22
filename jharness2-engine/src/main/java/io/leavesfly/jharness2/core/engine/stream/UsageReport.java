package io.leavesfly.jharness2.core.engine.stream;

public class UsageReport extends StreamEvent {
    private final long inputTokens;
    private final long outputTokens;
    private final long totalTokens;
    private final double sessionCostUsd;

    public UsageReport(long inputTokens, long outputTokens, long totalTokens, double sessionCostUsd) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.sessionCostUsd = sessionCostUsd;
    }

    @Override
    public String getEventType() {
        return "usage_report";
    }

    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getTotalTokens() { return totalTokens; }
    public double getSessionCostUsd() { return sessionCostUsd; }
}

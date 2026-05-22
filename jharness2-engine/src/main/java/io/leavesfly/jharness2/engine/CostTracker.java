package io.leavesfly.jharness2.engine;

import java.util.concurrent.atomic.AtomicLong;

public class CostTracker {

    private final AtomicLong inputTokens = new AtomicLong(0);
    private final AtomicLong outputTokens = new AtomicLong(0);
    private String modelName = "unknown";

    public void addUsage(long input, long output) {
        inputTokens.addAndGet(input);
        outputTokens.addAndGet(output);
    }

    public long getInputTokens() { return inputTokens.get(); }
    public long getOutputTokens() { return outputTokens.get(); }
    public long getTotalTokens() { return inputTokens.get() + outputTokens.get(); }

    public double getSessionCostUsd() {
        return (inputTokens.get() * 0.001 + outputTokens.get() * 0.002) / 1000.0;
    }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public void reset() {
        inputTokens.set(0);
        outputTokens.set(0);
    }
}

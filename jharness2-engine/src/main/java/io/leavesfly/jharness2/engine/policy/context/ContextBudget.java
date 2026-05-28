package io.leavesfly.jharness2.engine.policy.context;

/**
 * 上下文窗口预算管理 — 为不同组成部分预留 token 配额。
 * <p>
 * 确保在任何时刻发送给 LLM 的总 token 数不超过模型上下文窗口限制，
 * 同时为输出保留足够空间。
 *
 * <pre>
 * 总预算分配：
 * ┌──────────────────────────────────────────┐
 * │ maxContextTokens                         │
 * ├──────────────┬──────────┬────────────────┤
 * │ system +     │ history  │ reserved for   │
 * │ tools        │ messages │ output         │
 * └──────────────┴──────────┴────────────────┘
 * </pre>
 */
public class ContextBudget {

    private final int maxContextTokens;
    private final int reservedForOutput;
    private final int reservedForTools;
    private final int compactionThresholdRatio;

    private ContextBudget(Builder builder) {
        this.maxContextTokens = builder.maxContextTokens;
        this.reservedForOutput = builder.reservedForOutput;
        this.reservedForTools = builder.reservedForTools;
        this.compactionThresholdRatio = builder.compactionThresholdRatio;
    }

    /** 模型最大上下文窗口 token 数 */
    public int getMaxContextTokens() { return maxContextTokens; }

    /** 为 LLM 输出保留的 token 数 */
    public int getReservedForOutput() { return reservedForOutput; }

    /** 为工具 Schema 保留的 token 数 */
    public int getReservedForTools() { return reservedForTools; }

    /** 历史消息可用的最大 token 数 */
    public int getAvailableForHistory() {
        return maxContextTokens - reservedForOutput - reservedForTools;
    }

    /**
     * 判断当前消息 token 数是否超出预算，需要压缩。
     * @param currentTokens 当前消息 + system prompt 的 token 总数
     */
    public boolean needsCompaction(int currentTokens) {
        int threshold = (int) (getAvailableForHistory() * compactionThresholdRatio / 100.0);
        return currentTokens > threshold;
    }

    /**
     * 计算压缩后的目标 token 数（保留可用空间的 60%）。
     */
    public int getCompactionTargetTokens() {
        return (int) (getAvailableForHistory() * 0.6);
    }

    public static Builder builder() { return new Builder(); }

    /** 常用模型的预设配置 */
    public static ContextBudget forGpt4() {
        return builder().maxContextTokens(128000).reservedForOutput(16384).reservedForTools(4096).build();
    }

    public static ContextBudget forGpt4Mini() {
        return builder().maxContextTokens(128000).reservedForOutput(16384).reservedForTools(4096).build();
    }

    public static ContextBudget forClaude() {
        return builder().maxContextTokens(200000).reservedForOutput(16384).reservedForTools(8192).build();
    }

    public static ContextBudget forSmallModel() {
        return builder().maxContextTokens(8192).reservedForOutput(2048).reservedForTools(1024).build();
    }

    public static class Builder {
        private int maxContextTokens = 128000;
        private int reservedForOutput = 16384;
        private int reservedForTools = 4096;
        private int compactionThresholdRatio = 80;

        public Builder maxContextTokens(int tokens) { this.maxContextTokens = tokens; return this; }
        public Builder reservedForOutput(int tokens) { this.reservedForOutput = tokens; return this; }
        public Builder reservedForTools(int tokens) { this.reservedForTools = tokens; return this; }
        /** 达到可用空间的百分比时触发压缩（默认 80%） */
        public Builder compactionThresholdRatio(int percent) { this.compactionThresholdRatio = percent; return this; }
        public ContextBudget build() { return new ContextBudget(this); }
    }
}

package io.leavesfly.jharness2.core.spi;

/**
 * 按模型维度的用量统计摘要。
 */
public record ModelUsageSummary(
    String model,
    long inputTokens,
    long outputTokens,
    int requestCount
) {}

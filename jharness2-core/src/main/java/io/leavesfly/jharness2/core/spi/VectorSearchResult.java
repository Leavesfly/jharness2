package io.leavesfly.jharness2.core.spi;

/**
 * 向量搜索结果。
 */
public record VectorSearchResult(
    String memoryId,
    double score,
    String snippet
) {}

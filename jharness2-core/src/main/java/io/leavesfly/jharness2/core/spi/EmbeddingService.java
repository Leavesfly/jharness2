package io.leavesfly.jharness2.core.spi;

/**
 * 文本向量化 SPI —— 将文本转换为 embedding 向量。
 * <p>
 * 由外部 LLM embedding API（如 OpenAI text-embedding-ada-002）实现。
 */
public interface EmbeddingService {

    /**
     * 将文本转换为向量表示。
     *
     * @param text 输入文本
     * @return 浮点数组表示的 embedding 向量
     */
    float[] embed(String text);

    /**
     * 返回 embedding 维度。
     */
    int dimensions();
}

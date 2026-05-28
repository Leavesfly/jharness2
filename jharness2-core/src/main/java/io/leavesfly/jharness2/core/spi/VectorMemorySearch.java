package io.leavesfly.jharness2.core.spi;

import java.util.List;

/**
 * 向量语义搜索 SPI —— 增强记忆检索能力。
 * <p>
 * 将文本 embedding 化后做相似度检索,
 * 替代纯 LIKE 关键词匹配,提升记忆召回的准确性。
 */
public interface VectorMemorySearch {

    /** 索引一条记忆（生成 embedding 并存储） */
    void index(String userId, String project, String memoryId, String content);

    /** 语义搜索：返回与 query 最相似的 topK 条记忆 ID */
    List<VectorSearchResult> search(String userId, String project, String query, int topK);

    /** 删除指定记忆的向量索引 */
    void delete(String memoryId);

    /** 批量重建用户的向量索引 */
    void rebuildIndex(String userId, String project);
}

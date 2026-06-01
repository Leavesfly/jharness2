package io.leavesfly.jharness2.engine.ext.evolution.experience;

import java.util.List;

/**
 * 经验存储接口 —— 定义经验的持久化和检索操作。
 */
public interface ExperienceStore {

    /**
     * 保存一条经验。如果超出用户上限，自动淘汰最旧且低分的经验。
     */
    void save(Experience experience);

    /**
     * 基于关键词匹配检索相关经验。
     *
     * @param userId 用户 ID
     * @param query  查询文本（会被分词后与 keywords 匹配）
     * @param topK   最多返回条数
     * @return 按相关性排序的经验列表
     */
    List<Experience> search(String userId, String query, int topK);

    /**
     * 获取用户最近的 N 条经验。
     */
    List<Experience> getRecent(String userId, int limit);

    /**
     * 删除指定经验。
     */
    void delete(String experienceId);

    /**
     * 更新经验的相关性评分（被成功引用时加分）。
     */
    void updateRelevanceScore(String experienceId, float score);

    /**
     * 获取用户经验总数。
     */
    int count(String userId);
}

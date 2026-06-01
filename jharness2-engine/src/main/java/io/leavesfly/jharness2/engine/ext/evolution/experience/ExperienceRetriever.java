package io.leavesfly.jharness2.engine.ext.evolution.experience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 经验检索器 —— 根据当前任务描述检索相关经验，并格式化为可注入 system prompt 的文本。
 */
public class ExperienceRetriever {

    private static final Logger logger = LoggerFactory.getLogger(ExperienceRetriever.class);

    private final ExperienceStore store;
    private final int maxExperiencesInPrompt;

    public ExperienceRetriever(ExperienceStore store, int maxExperiencesInPrompt) {
        this.store = store;
        this.maxExperiencesInPrompt = maxExperiencesInPrompt;
    }

    /**
     * 根据用户输入检索相关经验，并构建可追加到 system prompt 的文本段。
     *
     * @param userId          用户 ID
     * @param taskDescription 当前任务描述（通常是用户的第一条消息）
     * @return 格式化的经验文本段（为空则无相关经验）
     */
    public String buildExperienceSection(String userId, String taskDescription) {
        List<Experience> relevant = store.search(userId, taskDescription, maxExperiencesInPrompt);

        if (relevant.isEmpty()) {
            return "";
        }

        StringBuilder section = new StringBuilder();
        section.append("\n## 相关历史经验（来自你之前的任务总结）\n\n");

        for (int i = 0; i < relevant.size(); i++) {
            Experience exp = relevant.get(i);
            section.append(i + 1).append(". ");
            section.append("[").append(exp.isSuccess() ? "✓成功" : "✗失败").append("] ");
            section.append("**").append(exp.getTaskPattern()).append("**\n");

            if (exp.getStrategy() != null && !exp.getStrategy().isBlank()) {
                section.append("   - 策略: ").append(exp.getStrategy()).append("\n");
            }
            if (exp.getLesson() != null && !exp.getLesson().isBlank()) {
                section.append("   - 教训: ").append(exp.getLesson()).append("\n");
            }
            if (exp.getToolsUsed() != null && !exp.getToolsUsed().isEmpty()) {
                section.append("   - 工具: ").append(String.join(", ", exp.getToolsUsed())).append("\n");
            }
            section.append("\n");

            // 更新相关性评分（被引用即加分）
            store.updateRelevanceScore(exp.getId(), exp.getRelevanceScore() + 0.1f);
        }

        section.append("请参考以上经验，但不要生搬硬套，根据当前具体情况灵活处理。\n");

        logger.debug("Retrieved {} experiences for user={}, query='{}'",
                relevant.size(), userId, truncate(taskDescription, 50));
        return section.toString();
    }

    /**
     * 检查用户是否有任何经验记录。
     */
    public boolean hasExperiences(String userId) {
        return store.count(userId) > 0;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}

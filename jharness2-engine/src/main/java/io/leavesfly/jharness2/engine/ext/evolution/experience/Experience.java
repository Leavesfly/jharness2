package io.leavesfly.jharness2.engine.ext.evolution.experience;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 经验数据模型 —— 从一次会话中提取的可复用知识。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Experience {

    private String id;
    private String userId;
    private String taskPattern;
    private List<String> toolsUsed;
    private String strategy;
    private boolean success;
    private String lesson;
    private List<String> keywords;
    private int turnsUsed;
    private long tokensUsed;
    private Instant createdAt;
    private float relevanceScore;

    public Experience() {}

    public static Experience create(String userId, String taskPattern, List<String> toolsUsed,
                                    String strategy, boolean success, String lesson,
                                    List<String> keywords, int turnsUsed, long tokensUsed) {
        Experience exp = new Experience();
        exp.id = UUID.randomUUID().toString();
        exp.userId = userId;
        exp.taskPattern = taskPattern;
        exp.toolsUsed = toolsUsed;
        exp.strategy = strategy;
        exp.success = success;
        exp.lesson = lesson;
        exp.keywords = keywords;
        exp.turnsUsed = turnsUsed;
        exp.tokensUsed = tokensUsed;
        exp.createdAt = Instant.now();
        exp.relevanceScore = 0.0f;
        return exp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTaskPattern() { return taskPattern; }
    public void setTaskPattern(String taskPattern) { this.taskPattern = taskPattern; }

    public List<String> getToolsUsed() { return toolsUsed; }
    public void setToolsUsed(List<String> toolsUsed) { this.toolsUsed = toolsUsed; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getLesson() { return lesson; }
    public void setLesson(String lesson) { this.lesson = lesson; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public int getTurnsUsed() { return turnsUsed; }
    public void setTurnsUsed(int turnsUsed) { this.turnsUsed = turnsUsed; }

    public long getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(long tokensUsed) { this.tokensUsed = tokensUsed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public float getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(float relevanceScore) { this.relevanceScore = relevanceScore; }
}

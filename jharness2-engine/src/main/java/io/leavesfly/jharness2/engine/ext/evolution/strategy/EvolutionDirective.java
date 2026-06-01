package io.leavesfly.jharness2.engine.ext.evolution.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 进化指令 —— 策略优化后生成的行为指导。
 * <p>
 * 包含追加到 system prompt 的优化建议、工具偏好、建议参数调整等。
 * 每次策略进化生成新版本，支持回滚。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvolutionDirective {

    private String id;
    private int version;
    private Instant createdAt;
    private String promptAddendum;
    private Map<String, Integer> toolPreference;
    private int suggestedMaxTurns;
    private List<String> avoidPatterns;
    private float effectivenessScore;
    private String previousId;

    public EvolutionDirective() {
        this.createdAt = Instant.now();
    }

    public static EvolutionDirective create(int version, String promptAddendum,
                                            Map<String, Integer> toolPreference,
                                            int suggestedMaxTurns, List<String> avoidPatterns) {
        EvolutionDirective directive = new EvolutionDirective();
        directive.id = "directive-v" + version + "-" + System.currentTimeMillis();
        directive.version = version;
        directive.promptAddendum = promptAddendum;
        directive.toolPreference = toolPreference;
        directive.suggestedMaxTurns = suggestedMaxTurns;
        directive.avoidPatterns = avoidPatterns;
        directive.effectivenessScore = 0.0f;
        return directive;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getPromptAddendum() { return promptAddendum; }
    public void setPromptAddendum(String promptAddendum) { this.promptAddendum = promptAddendum; }

    public Map<String, Integer> getToolPreference() { return toolPreference; }
    public void setToolPreference(Map<String, Integer> toolPreference) { this.toolPreference = toolPreference; }

    public int getSuggestedMaxTurns() { return suggestedMaxTurns; }
    public void setSuggestedMaxTurns(int suggestedMaxTurns) { this.suggestedMaxTurns = suggestedMaxTurns; }

    public List<String> getAvoidPatterns() { return avoidPatterns; }
    public void setAvoidPatterns(List<String> avoidPatterns) { this.avoidPatterns = avoidPatterns; }

    public float getEffectivenessScore() { return effectivenessScore; }
    public void setEffectivenessScore(float effectivenessScore) { this.effectivenessScore = effectivenessScore; }

    public String getPreviousId() { return previousId; }
    public void setPreviousId(String previousId) { this.previousId = previousId; }
}

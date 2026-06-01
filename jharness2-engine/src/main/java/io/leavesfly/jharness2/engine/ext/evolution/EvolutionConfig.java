package io.leavesfly.jharness2.engine.ext.evolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 进化子系统配置 —— 控制经验记忆、工具自生成、策略进化的行为。
 * <p>
 * 从 workspace/.jharness2/evolution/config.json 加载，
 * 也可由 core 层的 EvolutionCustomizer 从 Spring 配置注入。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvolutionConfig {

    private static final Logger logger = LoggerFactory.getLogger(EvolutionConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean enabled = false;

    // --- Level 1: 经验记忆 ---
    private boolean experienceEnabled = true;
    private int maxExperiencesPerUser = 100;
    private boolean autoExtractOnSessionEnd = true;
    private int minTurnsToExtract = 3;
    private int maxExperiencesInPrompt = 3;

    // --- Level 2: 工具自生成 ---
    private boolean toolMakerEnabled = false;
    private int maxToolsPerUser = 10;
    private int compileTimeoutSeconds = 10;
    private int executeTimeoutSeconds = 5;
    private List<String> deniedImports = List.of(
            "java.lang.reflect", "sun.misc", "sun.unsafe"
    );

    // --- Level 3: 策略进化 ---
    private boolean strategyEnabled = false;
    private int evaluationWindow = 10;
    private int abTestSessions = 20;
    private int maxDirectiveLength = 500;

    /**
     * 从 workspace 目录加载配置，不存在则返回默认配置。
     */
    public static EvolutionConfig load(Path workspace) {
        Path configFile = workspace.resolve(".jharness2/evolution/config.json");
        if (Files.exists(configFile)) {
            try {
                return MAPPER.readValue(configFile.toFile(), EvolutionConfig.class);
            } catch (IOException e) {
                logger.warn("Failed to load evolution config from {}, using defaults: {}",
                        configFile, e.getMessage());
            }
        }
        return new EvolutionConfig();
    }

    /**
     * 保存配置到 workspace。
     */
    public void save(Path workspace) {
        Path configFile = workspace.resolve(".jharness2/evolution/config.json");
        try {
            Files.createDirectories(configFile.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), this);
        } catch (IOException e) {
            logger.error("Failed to save evolution config: {}", e.getMessage());
        }
    }

    // --- Getters and Setters ---

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isExperienceEnabled() { return experienceEnabled; }
    public void setExperienceEnabled(boolean experienceEnabled) { this.experienceEnabled = experienceEnabled; }

    public int getMaxExperiencesPerUser() { return maxExperiencesPerUser; }
    public void setMaxExperiencesPerUser(int maxExperiencesPerUser) { this.maxExperiencesPerUser = maxExperiencesPerUser; }

    public boolean isAutoExtractOnSessionEnd() { return autoExtractOnSessionEnd; }
    public void setAutoExtractOnSessionEnd(boolean autoExtractOnSessionEnd) { this.autoExtractOnSessionEnd = autoExtractOnSessionEnd; }

    public int getMinTurnsToExtract() { return minTurnsToExtract; }
    public void setMinTurnsToExtract(int minTurnsToExtract) { this.minTurnsToExtract = minTurnsToExtract; }

    public int getMaxExperiencesInPrompt() { return maxExperiencesInPrompt; }
    public void setMaxExperiencesInPrompt(int maxExperiencesInPrompt) { this.maxExperiencesInPrompt = maxExperiencesInPrompt; }

    public boolean isToolMakerEnabled() { return toolMakerEnabled; }
    public void setToolMakerEnabled(boolean toolMakerEnabled) { this.toolMakerEnabled = toolMakerEnabled; }

    public int getMaxToolsPerUser() { return maxToolsPerUser; }
    public void setMaxToolsPerUser(int maxToolsPerUser) { this.maxToolsPerUser = maxToolsPerUser; }

    public int getCompileTimeoutSeconds() { return compileTimeoutSeconds; }
    public void setCompileTimeoutSeconds(int compileTimeoutSeconds) { this.compileTimeoutSeconds = compileTimeoutSeconds; }

    public int getExecuteTimeoutSeconds() { return executeTimeoutSeconds; }
    public void setExecuteTimeoutSeconds(int executeTimeoutSeconds) { this.executeTimeoutSeconds = executeTimeoutSeconds; }

    public List<String> getDeniedImports() { return deniedImports; }
    public void setDeniedImports(List<String> deniedImports) { this.deniedImports = deniedImports; }

    public boolean isStrategyEnabled() { return strategyEnabled; }
    public void setStrategyEnabled(boolean strategyEnabled) { this.strategyEnabled = strategyEnabled; }

    public int getEvaluationWindow() { return evaluationWindow; }
    public void setEvaluationWindow(int evaluationWindow) { this.evaluationWindow = evaluationWindow; }

    public int getAbTestSessions() { return abTestSessions; }
    public void setAbTestSessions(int abTestSessions) { this.abTestSessions = abTestSessions; }

    public int getMaxDirectiveLength() { return maxDirectiveLength; }
    public void setMaxDirectiveLength(int maxDirectiveLength) { this.maxDirectiveLength = maxDirectiveLength; }
}

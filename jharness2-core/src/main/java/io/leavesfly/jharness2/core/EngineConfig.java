package io.leavesfly.jharness2.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "jharness2.engine")
public class EngineConfig {

    private String defaultModel = "qwen3.5:4b";
    private String defaultBaseUrl = "http://localhost:11434/v1";
    private String defaultApiKey = "ollama";
    private int maxTokens = 4096;
    private int maxTurns = 12;
    private int connectTimeoutSeconds = 30;
    private int readTimeoutSeconds = 300;
    private int writeTimeoutSeconds = 30;
    private int maxEnginesPerUser = 5;
    private int maxTotalEngines = 200;
    private int engineIdleTimeoutMinutes = 30;
    private List<String> deniedCommandPatterns = new ArrayList<>(List.of(
            "rm -rf /*", "sudo *", "shutdown*", "reboot*", "mkfs*", "dd if=*"
    ));

    // all getters and setters
    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public String getDefaultBaseUrl() { return defaultBaseUrl; }
    public void setDefaultBaseUrl(String defaultBaseUrl) { this.defaultBaseUrl = defaultBaseUrl; }
    public String getDefaultApiKey() { return defaultApiKey; }
    public void setDefaultApiKey(String defaultApiKey) { this.defaultApiKey = defaultApiKey; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int maxTurns) { this.maxTurns = maxTurns; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    public int getWriteTimeoutSeconds() { return writeTimeoutSeconds; }
    public void setWriteTimeoutSeconds(int writeTimeoutSeconds) { this.writeTimeoutSeconds = writeTimeoutSeconds; }
    public int getMaxEnginesPerUser() { return maxEnginesPerUser; }
    public void setMaxEnginesPerUser(int maxEnginesPerUser) { this.maxEnginesPerUser = maxEnginesPerUser; }
    public int getMaxTotalEngines() { return maxTotalEngines; }
    public void setMaxTotalEngines(int maxTotalEngines) { this.maxTotalEngines = maxTotalEngines; }
    public int getEngineIdleTimeoutMinutes() { return engineIdleTimeoutMinutes; }
    public void setEngineIdleTimeoutMinutes(int engineIdleTimeoutMinutes) { this.engineIdleTimeoutMinutes = engineIdleTimeoutMinutes; }
    public List<String> getDeniedCommandPatterns() { return deniedCommandPatterns; }
    public void setDeniedCommandPatterns(List<String> deniedCommandPatterns) { this.deniedCommandPatterns = deniedCommandPatterns; }
}

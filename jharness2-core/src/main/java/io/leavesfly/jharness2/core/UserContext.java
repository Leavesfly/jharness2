package io.leavesfly.jharness2.core;

import java.nio.file.Path;

public class UserContext {
    private final String userId;
    private final String sessionId;
    private final Path workspace;
    private final String model;
    private final String apiKey;
    private final String baseUrl;

    public UserContext(String userId, String sessionId, Path workspace,
                       String model, String apiKey, String baseUrl) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.workspace = workspace;
        this.model = model;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public Path getWorkspace() { return workspace; }
    public String getModel() { return model; }
    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }

    public String getCacheKey() {
        return userId + ":" + sessionId;
    }
}

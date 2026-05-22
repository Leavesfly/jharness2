package io.leavesfly.jharness2.web.dto;

import jakarta.validation.constraints.NotBlank;

public class OnboardingRequest {

    private String displayName;

    @NotBlank(message = "API Key is required")
    private String apiKey;

    private String baseUrl;

    private String preferredModel;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getPreferredModel() { return preferredModel; }
    public void setPreferredModel(String preferredModel) { this.preferredModel = preferredModel; }
}

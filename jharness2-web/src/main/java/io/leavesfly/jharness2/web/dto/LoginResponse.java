package io.leavesfly.jharness2.web.dto;

public class LoginResponse {
    private String token;
    private String username;
    private long expiresIn;
    private boolean onboardingCompleted;

    public LoginResponse(String token, String username, long expiresIn, boolean onboardingCompleted) {
        this.token = token;
        this.username = username;
        this.expiresIn = expiresIn;
        this.onboardingCompleted = onboardingCompleted;
    }

    public String getToken() { return token; }
    public String getUsername() { return username; }
    public long getExpiresIn() { return expiresIn; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
}

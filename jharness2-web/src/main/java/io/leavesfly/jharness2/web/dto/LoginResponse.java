package io.leavesfly.jharness2.web.dto;

public class LoginResponse {
    private String token;
    private String username;
    private long expiresIn;

    public LoginResponse(String token, String username, long expiresIn) {
        this.token = token;
        this.username = username;
        this.expiresIn = expiresIn;
    }

    public String getToken() { return token; }
    public String getUsername() { return username; }
    public long getExpiresIn() { return expiresIn; }
}

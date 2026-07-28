package io.leavesfly.jharness2.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 * <p>
 * username 同时充当多用户隔离键（workspace 目录名、会话缓存 key 的一段），
 * 因此必须限制字符集：既避免不同用户名归一化后撞到同一目录，
 * 也避免 {@code :} 之类字符污染 {@code userId:sessionId} 形式的缓存 key。
 */
public class RegisterRequest {

    @NotBlank(message = "Username must not be blank")
    @Pattern(regexp = "[A-Za-z0-9_-]{3,32}",
            message = "Username must be 3-32 chars of letters, digits, underscore or hyphen")
    private String username;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, max = 128, message = "Password must be 8-128 chars")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

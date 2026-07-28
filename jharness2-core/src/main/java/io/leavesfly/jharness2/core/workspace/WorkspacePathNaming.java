package io.leavesfly.jharness2.core.workspace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 用户工作空间目录名的规范化工具。
 * <p>
 * 直接把非法字符替换为下划线会导致不同 userId 映射到同一目录
 * （例如 {@code "a b"} 与 {@code "a_b"}），在多用户场景下等于工作区互相可见。
 * 因此只要发生了字符替换或长度截断，就追加一段原始 userId 的哈希后缀来保证唯一性；
 * 未发生替换时保持原样，兼容既有目录。
 */
public final class WorkspacePathNaming {

    /** 目录名最大长度（预留哈希后缀空间，兼容各文件系统限制） */
    private static final int MAX_NAME_LENGTH = 48;
    private static final int HASH_LENGTH = 10;

    private WorkspacePathNaming() {
    }

    /**
     * 把 userId 转换为安全且不会与其他 userId 冲突的目录名。
     */
    public static String toDirectoryName(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        String sanitized = userId.replaceAll("[^a-zA-Z0-9._-]", "_");
        // 消除 "." 开头（隐藏目录）与 ".." 序列（路径穿越语义）
        String normalized = sanitized.replace("..", "_").replaceAll("^\\.+", "_");
        boolean tooLong = normalized.length() > MAX_NAME_LENGTH;
        if (normalized.equals(userId) && !tooLong) {
            return normalized;
        }
        String prefix = tooLong ? normalized.substring(0, MAX_NAME_LENGTH) : normalized;
        return prefix + "-" + shortHash(userId);
    }

    private static String shortHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，理论不可达
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

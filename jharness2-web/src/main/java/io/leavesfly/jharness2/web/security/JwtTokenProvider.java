package io.leavesfly.jharness2.web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    /** HS256 要求密钥不短于 256 bit */
    private static final int MIN_SECRET_BYTES = 32;

    /** 角色 claim 名 */
    public static final String CLAIM_ROLE = "role";
    /** 默认角色 */
    public static final String ROLE_USER = "USER";

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jharness2.security.jwt.secret:}") String secret,
            @Value("${jharness2.security.jwt.expiration-ms:86400000}") long expirationMs) {
        this.secretKey = resolveKey(secret);
        this.expirationMs = expirationMs;
    }

    /**
     * 解析签名密钥。
     * <p>
     * 未配置或密钥过短时不再退化到内置弱密钥，而是生成一个进程级随机密钥：
     * 开发环境仍可直接启动（代价是重启后旧 token 失效），而不会因忘配环境变量
     * 而静默地用一个公开在代码库里的密钥对外提供服务。
     */
    private static SecretKey resolveKey(String secret) {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES) {
            return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        if (secret != null && !secret.isBlank()) {
            logger.error("jharness2.security.jwt.secret 长度不足 {} 字节，已改用随机密钥（重启后 token 将失效）",
                    MIN_SECRET_BYTES);
        } else {
            logger.warn("未配置 jharness2.security.jwt.secret，已生成临时随机密钥；生产环境请通过 JWT_SECRET 注入");
        }
        return Jwts.SIG.HS256.key().build();
    }

    public String generateToken(String username) {
        return generateToken(username, ROLE_USER);
    }

    /**
     * 签发带角色的 token。角色写入 claim 后才能在无状态鉴权中做 RBAC。
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, normalizeRole(role))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 从 token 中读取角色，缺失时退化为 USER（兼容旧 token）。
     */
    public String getRoleFromToken(String token) {
        Object role = parseClaims(token).get(CLAIM_ROLE);
        return role != null ? normalizeRole(role.toString()) : ROLE_USER;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return ROLE_USER;
        String upper = role.trim().toUpperCase();
        return upper.startsWith("ROLE_") ? upper.substring(5) : upper;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}

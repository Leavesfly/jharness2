package io.leavesfly.jharness2.web.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

/**
 * 鉴权辅助：统一角色判定与"跨用户访问"授权收口。
 * <p>
 * 多用户服务中，凡是接受 userId 入参的接口都必须经由此处判定，
 * 避免"已登录即可查任意用户数据"的横向越权。
 */
public final class AuthzSupport {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private AuthzSupport() {
    }

    public static boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (ROLE_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析请求想要访问的目标用户：未指定或指向自己时返回当前用户；
     * 指向他人时要求管理员角色，否则 403。
     *
     * @param auth            当前认证信息
     * @param requestedUserId 请求参数中的目标 userId（可为 null）
     * @return 允许访问的 userId
     */
    public static String resolveTargetUserId(Authentication auth, String requestedUserId) {
        String currentUser = auth.getName();
        if (requestedUserId == null || requestedUserId.isBlank() || requestedUserId.equals(currentUser)) {
            return currentUser;
        }
        if (!isAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Not allowed to access data of another user");
        }
        return requestedUserId;
    }
}

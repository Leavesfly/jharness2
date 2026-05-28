package io.leavesfly.jharness2.web.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 多租户过滤器 —— 从请求头或认证信息中提取租户 ID 并设置到 TenantContext。
 * <p>
 * 优先级：
 * 1. 请求头 X-Tenant-Id（显式指定）
 * 2. 认证用户关联的默认组织
 * 3. 默认租户 "default"
 * <p>
 * 在请求结束后自动清理 ThreadLocal，防止内存泄漏。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String DEFAULT_TENANT = "default";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = resolveTenantId(request);
            TenantContext.setTenantId(tenantId);
            logger.debug("Tenant resolved: {} for URI: {}", tenantId, request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveTenantId(HttpServletRequest request) {
        // 优先从请求头获取
        String headerTenant = request.getHeader(TENANT_HEADER);
        if (headerTenant != null && !headerTenant.isBlank()) {
            return headerTenant.trim();
        }

        // 尝试从认证信息中提取组织（扩展点：可对接用户-组织映射表）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            // 未来可从 UserDetails 中获取 organization 字段
            // 当前阶段使用默认租户
            return DEFAULT_TENANT;
        }

        return DEFAULT_TENANT;
    }
}

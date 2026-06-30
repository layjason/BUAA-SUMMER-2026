package io.github.layjason.mayoistar.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前用户持有者，从 SecurityContext 中获取当前登录用户 ID。
 *
 * <p>类职责：为 Service 层提供统一的当前用户身份获取方式。
 *
 * <p>类不变量：在已认证请求中返回 JWT 中的真实用户 ID，未认证时返回占位用户 ID。
 *
 * <p>前置条件：JwtAuthenticationFilter 已完成认证并设置 SecurityContext。
 *
 * <p>后置条件：返回当前请求用户的 userId。
 */
@Component
public class CurrentUser {

    private static final String PLACEHOLDER_USER_ID = "00000000-0000-0000-0000-000000000001";

    /**
     * 获取当前登录用户 ID。
     *
     * <p>前置条件：若请求已通过 JWT 认证，则 SecurityContext 中包含 userId。
     * 否则返回占位用户 ID（用于测试场景）。
     *
     * <p>后置条件：返回当前用户 ID，非空。
     */
    public String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String userId) {
            return userId;
        }
        return PLACEHOLDER_USER_ID;
    }
}

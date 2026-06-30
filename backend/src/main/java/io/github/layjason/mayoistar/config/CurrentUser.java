package io.github.layjason.mayoistar.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前用户持有者，从 SecurityContext 中获取当前登录用户 ID。
 *
 * <p>类职责：为 Controller 层提供统一的当前用户身份获取方式。
 *
 * <p>前置条件：JwtAuthenticationFilter（或 TestSecurityConfiguration）
 * 已完成认证并设置 SecurityContext。
 *
 * <p>后置条件：返回当前请求用户的 userId，非空。
 */
@Component
public class CurrentUser {

    /**
     * 获取当前登录用户 ID。
     *
     * <p>前置条件：SecurityContext 中已设置认证信息，principal 为 userId 字符串。
     *
     * <p>后置条件：返回当前用户 ID。
     *
     * @throws IllegalStateException 未认证时抛出
     */
    public String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String userId) {
            return userId;
        }
        throw new IllegalStateException("无法获取当前用户：SecurityContext 中无有效认证信息");
    }
}

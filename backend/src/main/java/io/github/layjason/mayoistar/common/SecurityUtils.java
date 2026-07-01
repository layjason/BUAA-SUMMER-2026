package io.github.layjason.mayoistar.common;

import io.github.layjason.mayoistar.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * 安全工具类，从 SecurityContext 中提取当前登录用户信息。
 *
 * <p>类职责：为各 Controller 和 Service 提供统一的当前用户 ID 获取方式，
 * 支持 JWT 认证（principal 为 userId String）和测试 {@code @WithMockUser}（principal 为 UserDetails）场景。
 *
 * <p>类不变量：该方法不修改 SecurityContext 或数据库状态。
 */
@Slf4j
@Component
public class SecurityUtils {

    /**
     * 从 SecurityContext 中提取当前登录用户 ID。
     *
     * <p>前置条件：请求已通过 JWT 过滤器认证或测试场景注入。
     * SecurityContext 中存在有效的 Authentication。
     *
     * <p>后置条件：返回当前用户 ID 字符串。若未认证则抛出 BusinessException(401)。
     *
     * @return 当前用户 ID
     */
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BusinessException(401, "Authentication is required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String userId) {
            return userId;
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        throw new BusinessException(401, "Authentication is required");
    }
}

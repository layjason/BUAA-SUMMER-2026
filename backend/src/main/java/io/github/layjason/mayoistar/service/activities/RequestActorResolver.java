package io.github.layjason.mayoistar.service.activities;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * 解析当前请求的调用者标识。
 *
 * <p>类职责：从 Spring Security 的 SecurityContext 中提取已认证用户的 ID，避免业务服务直接依赖
 * Security API。
 *
 * <p>类不变量：仅解析 SecurityContext 中的只读认证信息，不创建用户、不持久化任何状态。
 */
@Component
public class RequestActorResolver {

    /**
     * 从 SecurityContext 中读取当前已认证用户的 ID。
     *
     * <p>前置条件：调用发生在已通过 JWT 认证的请求线程内；若未认证则返回空。
     *
     * <p>后置条件：当 SecurityContext 中存在有效的认证信息且 principal 为非空字符串（JWT 场景）或
     * UserDetails 的 username（测试场景）时返回该值，否则返回空。
     *
     * <p>不变量：该方法只做认证信息解析，不进行数据库访问。
     *
     * @return 当前请求的已认证用户 ID
     */
    public Optional<String> resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        // JWT 认证场景：principal 为 userId 字符串
        if (principal instanceof String userId && !userId.isBlank()) {
            return Optional.of(userId);
        }
        // Spring Security Test 或其它 UserDetails 场景：从 username 获取
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            if (username != null && !username.isBlank()) {
                return Optional.of(username);
            }
        }
        return Optional.empty();
    }
}

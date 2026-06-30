package io.github.layjason.mayoistar.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityUtils 单元测试。
 *
 * <p>类职责：验证从 SecurityContext 中提取当前用户 ID 的各类场景。
 *
 * <p>类不变量：每次测试后清理 SecurityContextHolder。
 */
class SecurityUtilsTest {

    private final SecurityUtils securityUtils = new SecurityUtils();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 验证 JWT 场景下 principal 为 String 时能正确提取 userId。
     *
     * <p>前置条件：SecurityContext 中 Authentication.principal 为 String。
     *
     * <p>后置条件：返回 principal 字符串。
     */
    @Test
    void returnsUserIdWhenPrincipalIsString() {
        var auth = new UsernamePasswordAuthenticationToken(
                "user-123", null, List.of(new SimpleGrantedAuthority("ROLE_personal")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        String userId = securityUtils.getCurrentUserId();
        assertThat(userId).isEqualTo("user-123");
    }

    /**
     * 验证 @WithMockUser 测试场景下 principal 为 UserDetails 时能正确提取 userId。
     *
     * <p>前置条件：SecurityContext 中 Authentication.principal 为 UserDetails。
     *
     * <p>后置条件：返回 UserDetails.getUsername()。
     */
    @Test
    void returnsUserIdWhenPrincipalIsUserDetails() {
        var userDetails = org.springframework.security.core.userdetails.User.withUsername("test-user")
                .password("password")
                .authorities("ROLE_personal")
                .build();
        var auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String userId = securityUtils.getCurrentUserId();
        assertThat(userId).isEqualTo("test-user");
    }

    /**
     * 验证未认证时抛出 BusinessException(401)。
     *
     * <p>前置条件：SecurityContext 中没有 Authentication。
     *
     * <p>后置条件：抛出 BusinessException，code=401。
     */
    @Test
    void throwsExceptionWhenNotAuthenticated() {
        assertThatThrownBy(() -> securityUtils.getCurrentUserId())
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }

    /**
     * 验证 principal 为未知类型时抛出 BusinessException(401)。
     *
     * <p>前置条件：SecurityContext 中 Authentication.principal 为不支持的类型。
     *
     * <p>后置条件：抛出 BusinessException，code=401。
     */
    @Test
    void throwsExceptionWhenPrincipalIsUnknownType() {
        var auth = new UsernamePasswordAuthenticationToken(
                12345, null, List.of(new SimpleGrantedAuthority("ROLE_personal")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> securityUtils.getCurrentUserId())
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }
}

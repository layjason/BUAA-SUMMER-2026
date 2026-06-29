package io.github.layjason.mayoistar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 契约骨架阶段的安全配置。
 *
 * <p>类职责：在真实 JWT 鉴权接入前放行 API 请求，避免 Spring Security 默认登录页影响契约测试。
 *
 * <p>类不变量：该配置不创建用户会话，不实现业务鉴权。
 */
@Configuration
public class SecurityConfiguration {

    /**
     * 创建允许所有请求通过的过滤链。
     *
     * <p>前置条件：当前后端仍处于 API 契约骨架阶段，尚未接入 JWT 认证。
     *
     * <p>后置条件：所有 HTTP 请求均可到达 Controller，CSRF 校验被关闭以支持占位 POST/DELETE 接口。
     *
     * <p>不变量：该方法只配置 Spring Security，不读取或修改业务数据。
     *
     * @param http Spring Security HTTP 配置器
     * @return 安全过滤链
     * @throws Exception 当安全配置构建失败时抛出
     */
    @Bean
    public SecurityFilterChain apiContractSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/identity/**", "/activities/**", "/ai/**", "/social/**", "/chat/**", "/admin/**"))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}

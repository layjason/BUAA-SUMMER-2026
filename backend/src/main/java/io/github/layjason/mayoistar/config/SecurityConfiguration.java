package io.github.layjason.mayoistar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置。
 *
 * <p>类职责：配置 JWT 无状态认证、角色路由规则、方法级安全、密码编码器。
 *
 * <p>不变量：不使用 Session，所有认证信息由 JWT 承载。路由级规则优先于方法级注解。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/identity/auth/register/**",
        "/identity/auth/login",
        "/identity/auth/activate",
        "/identity/auth/activation-email",
        "/identity/auth/password-reset-email",
        "/identity/auth/password-reset",
        "/identity/auth/refresh",
        "/identity/nicknames/availability",
        "/identity/interest-tags",
        "/admin/auth/login",
    };

    private static final String[] MERCHANT_ENDPOINTS = {
        "/identity/me/merchant-profile", "/identity/me/merchant-qualification", "/identity/media/license",
    };

    private static final String[] WEBSOCKET_ENDPOINTS = {
        "/chat/ws/**",
    };

    /**
     * 创建安全过滤链。
     *
     * <p>后置条件：
     * <ul>
     *   <li>公开端点无需认证即可访问</li>
     *   <li>/admin/** 端点需要 ROLE_admin</li>
     *   <li>商家专属端点需要 ROLE_merchant</li>
     *   <li>其余端点需要 JWT Bearer Token 认证</li>
     * </ul>
     *
     * @param http      Spring Security HTTP 配置器
     * @param jwtFilter JWT 认证过滤器
     * @return 安全过滤链
     * @throws Exception 配置失败时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(WEBSOCKET_ENDPOINTS)
                        .permitAll()
                        .requestMatchers("/admin/**")
                        .hasRole("admin")
                        .requestMatchers(MERCHANT_ENDPOINTS)
                        .hasRole("merchant")
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 密码编码器 Bean。
     *
     * <p>后置条件：返回 BCryptPasswordEncoder，强度为 12。
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

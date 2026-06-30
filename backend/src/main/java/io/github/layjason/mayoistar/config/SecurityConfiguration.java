package io.github.layjason.mayoistar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * <p>类职责：配置 JWT 无状态认证、公开/受保护端点路由、密码编码器。
 *
 * <p>类不变量：不使用 Session，所有认证信息由 JWT 承载。
 */
@Configuration
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
        "/admin/auth/password",
    };

    /**
     * 创建安全过滤链。
     *
     * <p>后置条件：公开端点无需认证即可访问，其余端点需要 JWT Bearer Token。
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

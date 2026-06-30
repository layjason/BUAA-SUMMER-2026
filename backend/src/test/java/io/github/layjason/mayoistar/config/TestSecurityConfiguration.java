package io.github.layjason.mayoistar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 测试安全配置，允许所有请求通过并注入测试用户。
 *
 * <p>类职责：在 test profile 下替代主 SecurityConfiguration，
 * 确保集成测试不需要真实 JWT 鉴权，同时为 CurrentUser 提供有效认证信息。
 */
@TestConfiguration
@Profile("test")
public class TestSecurityConfiguration {

    static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .addFilterBefore(
                        new OncePerRequestFilter() {
                            @Override
                            protected void doFilterInternal(
                                    HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                                    throws ServletException, IOException {
                                SecurityContextHolder.getContext()
                                        .setAuthentication(new UsernamePasswordAuthenticationToken(
                                                TEST_USER_ID,
                                                null,
                                                Collections.singletonList(
                                                        new SimpleGrantedAuthority("ROLE_personal"))));
                                filterChain.doFilter(request, response);
                            }
                        },
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

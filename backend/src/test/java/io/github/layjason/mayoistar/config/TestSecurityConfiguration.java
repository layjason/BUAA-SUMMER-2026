package io.github.layjason.mayoistar.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 测试安全配置，允许所有请求通过。
 *
 * <p>类职责：在 test profile 下替代主 SecurityConfiguration，
 * 确保 Service 集成测试不需要 JWT 鉴权。
 */
@TestConfiguration
@Profile("test")
public class TestSecurityConfiguration {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}

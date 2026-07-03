package io.github.layjason.mayoistar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * 测试安全辅助配置。
 *
 * <p>类职责：在 test profile 下补充测试上下文所需的安全相关基础 Bean。
 */
@TestConfiguration
@Profile("test")
public class TestSecurityConfiguration {

    /**
     * 提供 test profile 下的 ObjectMapper Bean。
     *
     * <p>AWS SDK 引入的 tools.jackson 可能导致 Spring Boot JacksonAutoConfiguration 不创建
     * com.fasterxml.jackson.databind.ObjectMapper Bean，此处显式提供以确保 SecurityErrorHandler 正常工作。
     */
    @Bean
    @Profile("test")
    public ObjectMapper testObjectMapper() {
        return new ObjectMapper();
    }
}

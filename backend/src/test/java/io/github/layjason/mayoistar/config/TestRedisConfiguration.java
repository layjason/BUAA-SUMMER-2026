package io.github.layjason.mayoistar.config;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 测试环境 Redis 配置。
 *
 * <p>类职责：在 test profile 下提供 Mock 的 StringRedisTemplate，
 * 避免依赖真实 Redis 服务。
 */
@TestConfiguration
@Profile("test")
public class TestRedisConfiguration {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return mock(StringRedisTemplate.class);
    }
}

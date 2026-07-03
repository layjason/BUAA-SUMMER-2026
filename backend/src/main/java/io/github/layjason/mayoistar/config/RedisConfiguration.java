package io.github.layjason.mayoistar.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Redis / Lettuce 客户端配置。
 *
 * <p>类职责：自定义 Lettuce 客户端连接参数，解决 Docker Desktop Windows 环境下 TCP 连接被
 * NAT 层静默丢弃的问题。
 *
 * <p>不变量：仅在非测试 Profile 下生效（测试 Profile 已排除 Redis 自动配置）。
 */
@Configuration
@Profile("!test")
public class RedisConfiguration {

    /**
     * 配置 Lettuce 客户端 Socket 和 Client 选项。
     *
     * <p>前置条件：Spring Boot 已加载 Redis 自动配置。
     *
     * <p>后置条件：Lettuce 连接启用 TCP keepalive 探测（60s 空闲后开始探测，间隔 30s，最多 3
     * 次），防止 NAT/代理层丢弃空闲连接；连接超时和命令超时均设置为合理值。
     *
     * @return LettuceClientConfigurationBuilderCustomizer 实例
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer() {
        SocketOptions.KeepAliveOptions keepAliveOptions = SocketOptions.KeepAliveOptions.builder()
                .enable()
                .idle(Duration.ofSeconds(60L))
                .interval(Duration.ofSeconds(30L))
                .count(3)
                .build();

        SocketOptions socketOptions = SocketOptions.builder()
                .keepAlive(keepAliveOptions)
                .connectTimeout(Duration.ofSeconds(3L))
                .build();

        TimeoutOptions timeoutOptions = TimeoutOptions.enabled(Duration.ofSeconds(5L));

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .pingBeforeActivateConnection(true)
                .timeoutOptions(timeoutOptions)
                .autoReconnect(true)
                .build();

        return clientConfigurationBuilder -> clientConfigurationBuilder.clientOptions(clientOptions);
    }
}

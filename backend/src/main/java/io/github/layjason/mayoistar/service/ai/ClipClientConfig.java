package io.github.layjason.mayoistar.service.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * CLIP 服务客户端配置。
 *
 * <p>类职责：提供经 Spring Boot 自动装配（含 Jackson message converter）的 RestClient.Builder，
 * 确保请求体可正确序列化为 JSON。
 */
@Configuration
public class ClipClientConfig {

    /**
     * 注册 RestClient.Builder bean。
     *
     * <p>前置条件：Jackson 在 classpath 上。
     *
     * <p>后置条件：Spring Boot 自动将 MappingJackson2HttpMessageConverter 注册到该 builder。
     *
     * @return 预配置的 RestClient.Builder
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}

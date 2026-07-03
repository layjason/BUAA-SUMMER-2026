package io.github.layjason.mayoistar.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * CLIP 服务客户端配置。
 *
 * <p>类职责：创建 RestClient bean，显式注册 Jackson message converter，
 * 确保请求体可正确序列化为 JSON。
 */
@Configuration
@RequiredArgsConstructor
public class ClipClientConfig {

    private final ObjectMapper objectMapper;

    /**
     * 创建 CLIP 服务 RestClient。
     *
     * <p>前置条件：AiProperties 中 clip.endpoint 为有效 URL；ObjectMapper 已由 Jackson 自动配置。
     *
     * <p>后置条件：返回可正确序列化 JSON 请求体的 RestClient。
     *
     * @param properties AI 配置属性
     * @return 预配置的 RestClient
     */
    @Bean
    public RestClient clipRestClient(AiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getClip().getEndpoint())
                .messageConverters(
                        converters -> converters.addFirst(new MappingJackson2HttpMessageConverter(objectMapper)))
                .build();
    }
}

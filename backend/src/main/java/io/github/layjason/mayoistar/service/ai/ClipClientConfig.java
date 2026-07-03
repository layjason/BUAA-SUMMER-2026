package io.github.layjason.mayoistar.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.config.AiProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * CLIP 服务客户端配置。
 *
 * <p>类职责：创建 RestClient bean，显式注册 Jackson message converter，
 * 确保请求体可正确序列化为 JSON，并使用 HTTP/1.1 兼容 FastAPI/Uvicorn 边车服务。
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
     * <p>后置条件：返回可正确序列化 JSON 请求体、不会发起 h2c 升级请求的 RestClient。
     *
     * <p>不变量：请求工厂只使用 JDK HttpURLConnection 的 HTTP/1.1 能力，避免 Uvicorn 将 HTTP/2 明文升级请求解析为异常请求。
     *
     * @param properties AI 配置属性
     * @return 预配置的 RestClient
     */
    @Bean
    public RestClient clipRestClient(AiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.getClip().getTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(properties.getClip().getEndpoint())
                .requestFactory(requestFactory)
                .messageConverters(
                        converters -> converters.addFirst(new MappingJackson2HttpMessageConverter(objectMapper)))
                .build();
    }
}

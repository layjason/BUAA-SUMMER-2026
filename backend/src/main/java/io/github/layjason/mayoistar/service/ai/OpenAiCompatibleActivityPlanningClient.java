package io.github.layjason.mayoistar.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI-compatible 活动策划客户端。
 *
 * <p>类职责：调用配置中的 Chat Completions 兼容接口，提取第一条 assistant 消息内容。
 *
 * <p>类不变量：未配置 endpoint/model/apiKey 时不发起 HTTP 请求；日志中不输出 API Token 和完整用户内容。
 */
@Slf4j
@Component
public class OpenAiCompatibleActivityPlanningClient implements ActivityPlanningClient {

    private final AiProperties.ActivityPlanning properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleActivityPlanningClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.properties = aiProperties.getActivityPlanning();
        this.objectMapper = objectMapper;
    }

    /**
     * 调用兼容 Chat Completions 的活动策划模型。
     *
     * <p>前置条件：prompt 非空；若配置完整，则 endpoint 指向兼容 Chat Completions 的完整接口地址。
     *
     * <p>后置条件：成功时返回 assistant content；失败时抛出契约业务异常。
     *
     * <p>不变量：不记录 prompt 原文和 API Token。
     *
     * @param prompt 活动策划提示词
     * @return 模型返回的 assistant content
     */
    @Override
    public String generate(String prompt) {
        if (!configured()) {
            throw new BusinessException(ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI service is unavailable");
        }

        try {
            JsonNode response = restClient()
                    .post()
                    .uri(properties.getEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(prompt))
                    .retrieve()
                    .body(JsonNode.class);
            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                log.warn("AI 活动策划模型返回空内容");
                throw new BusinessException(ErrorCodes.AI_OUTPUT_UNAVAILABLE, "AI output is unavailable");
            }
            return content;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("AI 活动策划 HTTP 调用失败: endpointConfigured=true");
            throw new BusinessException(ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI service is unavailable", exception);
        } catch (RuntimeException exception) {
            log.warn("AI 活动策划响应解析失败", exception);
            throw new BusinessException(ErrorCodes.AI_OUTPUT_UNAVAILABLE, "AI output is unavailable", exception);
        }
    }

    private boolean configured() {
        return !isBlank(properties.getEndpoint())
                && !isBlank(properties.getModel())
                && !isBlank(properties.getApiKey());
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .messageConverters(
                        converters -> converters.addFirst(new MappingJackson2HttpMessageConverter(objectMapper)))
                .build();
    }

    private Map<String, Object> requestBody(String prompt) {
        return Map.of(
                "model",
                properties.getModel(),
                "temperature",
                0.7,
                "messages",
                List.of(
                        Map.of("role", "system", "content", "你是一个只输出 JSON 的中文活动策划助手。"),
                        Map.of("role", "user", "content", prompt)));
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode content = response.at("/choices/0/message/content");
        return content.isMissingNode() || content.isNull() ? null : content.asText();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

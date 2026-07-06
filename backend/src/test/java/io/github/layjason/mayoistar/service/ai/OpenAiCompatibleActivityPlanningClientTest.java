package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OpenAiCompatibleActivityPlanningClient 单元测试。
 *
 * <p>类职责：验证 OpenAI-compatible 客户端的配置校验、请求构造和响应解析。
 *
 * <p>类不变量：测试仅启动本地 HttpServer，不调用真实 LLM API。
 */
class OpenAiCompatibleActivityPlanningClientTest {

    @Test
    @DisplayName("未配置模型服务时应抛出 AI 服务不可用业务异常")
    void shouldThrowServiceUnavailableWhenProviderNotConfigured() {
        OpenAiCompatibleActivityPlanningClient client =
                new OpenAiCompatibleActivityPlanningClient(new AiProperties(), new ObjectMapper());

        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCodes.AI_SERVICE_UNAVAILABLE);
                    assertThat(exception.getBusinessMessage()).isEqualTo("AI service is unavailable");
                });
    }

    @Test
    @DisplayName("调用模型服务时应发送授权头和 Chat Completions 请求体")
    void shouldSendChatCompletionsRequest() throws IOException {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> requestContentType = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/v1/chat/completions",
                exchange -> handleChatCompletionRequest(
                        exchange, requestMethod, requestContentType, authorization, requestBody));
        server.start();

        try {
            AiProperties properties =
                    properties("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
            OpenAiCompatibleActivityPlanningClient client =
                    new OpenAiCompatibleActivityPlanningClient(properties, new ObjectMapper());

            String content = client.generate("请策划羽毛球活动");

            assertThat(content).contains("AI 羽毛球活动");
            assertThat(requestMethod.get()).isEqualTo("POST");
            assertThat(requestContentType.get()).startsWith("application/json");
            assertThat(authorization.get()).isEqualTo("Bearer test-token");
            assertThat(requestBody.get()).contains("\"model\":\"test-model\"");
            assertThat(requestBody.get()).contains("请策划羽毛球活动");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("模型返回空消息时应抛出 AI 输出不可用业务异常")
    void shouldThrowOutputUnavailableWhenContentMissing() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, "{\"choices\":[]}"));
        server.start();

        try {
            AiProperties properties =
                    properties("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
            OpenAiCompatibleActivityPlanningClient client =
                    new OpenAiCompatibleActivityPlanningClient(properties, new ObjectMapper());

            assertThatThrownBy(() -> client.generate("prompt"))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getCode()).isEqualTo(ErrorCodes.AI_OUTPUT_UNAVAILABLE);
                        assertThat(exception.getBusinessMessage()).isEqualTo("AI output is unavailable");
                    });
        } finally {
            server.stop(0);
        }
    }

    private static AiProperties properties(String endpoint) {
        AiProperties properties = new AiProperties();
        properties.getActivityPlanning().setEndpoint(endpoint);
        properties.getActivityPlanning().setModel("test-model");
        properties.getActivityPlanning().setApiKey("test-token");
        properties.getActivityPlanning().setTimeoutSeconds(5);
        return properties;
    }

    private static void handleChatCompletionRequest(
            HttpExchange exchange,
            AtomicReference<String> requestMethod,
            AtomicReference<String> requestContentType,
            AtomicReference<String> authorization,
            AtomicReference<String> requestBody)
            throws IOException {
        requestMethod.set(exchange.getRequestMethod());
        requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        respond(exchange, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"title\\":\\"AI 羽毛球活动\\",\\"tags\\":[\\"运动\\"]}"
                      }
                    }
                  ]
                }
                """);
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }
}

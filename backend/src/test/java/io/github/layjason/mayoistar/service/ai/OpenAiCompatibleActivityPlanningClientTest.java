package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.layjason.mayoistar.config.AiProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OpenAiCompatibleActivityPlanningClient 单元测试。
 *
 * <p>类职责：验证 OpenAI-compatible 客户端的配置兜底、请求格式和响应解析。
 *
 * <p>类不变量：测试只使用本地 HttpServer，不调用真实 LLM API。
 */
class OpenAiCompatibleActivityPlanningClientTest {

    @Test
    @DisplayName("缺少接口配置时不应发起 HTTP 请求")
    void shouldReturnFailedWhenConfigurationMissing() {
        OpenAiCompatibleActivityPlanningClient client =
                new OpenAiCompatibleActivityPlanningClient(new AiProperties(), new ObjectMapper());

        ActivityPlanningClientResult result = client.generate("生成一个活动");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.friendlyErrorMessage()).isEqualTo("AI 活动策划接口尚未配置");
    }

    @Test
    @DisplayName("配置完整时应向配置的完整接口地址发送请求并解析响应")
    void shouldSendChatCompletionRequestAndParseContent() throws IOException {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> requestAuthorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/custom/activity-plans",
                exchange -> handleChatCompletionsRequest(
                        exchange, requestPath, requestMethod, requestAuthorization, requestBody));
        server.start();

        try {
            AiProperties properties = new AiProperties();
            properties
                    .getActivityPlanning()
                    .setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/custom/activity-plans");
            properties.getActivityPlanning().setModel("test-model");
            properties.getActivityPlanning().setApiKey("test-token");
            properties.getActivityPlanning().setTimeoutSeconds(5);
            OpenAiCompatibleActivityPlanningClient client =
                    new OpenAiCompatibleActivityPlanningClient(properties, new ObjectMapper());

            ActivityPlanningClientResult result = client.generate("生成一个活动");

            assertThat(result.succeeded()).isTrue();
            assertThat(result.content()).contains("周末桌游夜");
            assertThat(requestPath.get()).isEqualTo("/custom/activity-plans");
            assertThat(requestMethod.get()).isEqualTo("POST");
            assertThat(requestAuthorization.get()).isEqualTo("Bearer test-token");
            assertThat(requestBody.get()).contains("\"model\":\"test-model\"");
            assertThat(requestBody.get()).contains("\"messages\"");
            assertThat(requestBody.get()).contains("生成一个活动");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 处理测试中的 Chat Completions 请求。
     *
     * <p>前置条件：exchange 来自配置的活动策划接口路径，引用容器均非空。
     *
     * <p>后置条件：记录请求并返回包含 assistant content 的兼容响应。
     *
     * <p>不变量：不访问真实模型服务。
     */
    private static void handleChatCompletionsRequest(
            HttpExchange exchange,
            AtomicReference<String> requestPath,
            AtomicReference<String> requestMethod,
            AtomicReference<String> requestAuthorization,
            AtomicReference<String> requestBody)
            throws IOException {
        requestPath.set(exchange.getRequestURI().getPath());
        requestMethod.set(exchange.getRequestMethod());
        requestAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] responseBody = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"title\\":\\"周末桌游夜\\",\\"tags\\":[\\"桌游\\"],\\"suggestedCapacity\\":8}"
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }
}

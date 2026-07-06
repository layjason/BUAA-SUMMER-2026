package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * OpenAiCompatibleActivityPlanningClient 单元测试。
 *
 * <p>类职责：验证 OpenAI-compatible 客户端的配置校验、请求构造和响应解析。
 *
 * <p>类不变量：测试仅使用 MockRestServiceServer，不调用真实 LLM API，不绑定本地网络端口。
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
    void shouldSendChatCompletionsRequest() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder)
                .ignoreExpectOrder(true)
                .build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"test-model\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("请策划羽毛球活动")))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"title\\":\\"AI 羽毛球活动\\",\\"tags\\":[\\"运动\\"]}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        OpenAiCompatibleActivityPlanningClient client = OpenAiCompatibleActivityPlanningClient.forTest(
                properties("https://ai.example.test/v1/chat/completions"), new ObjectMapper(), restClientBuilder);

        String content = client.generate("请策划羽毛球活动");

        assertThat(content).contains("AI 羽毛球活动");
        server.verify();
    }

    @Test
    @DisplayName("模型返回空消息时应抛出 AI 输出不可用业务异常")
    void shouldThrowOutputUnavailableWhenContentMissing() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder)
                .ignoreExpectOrder(true)
                .build();
        server.expect(requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        OpenAiCompatibleActivityPlanningClient client = OpenAiCompatibleActivityPlanningClient.forTest(
                properties("https://ai.example.test/v1/chat/completions"), new ObjectMapper(), restClientBuilder);

        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCodes.AI_OUTPUT_UNAVAILABLE);
                    assertThat(exception.getBusinessMessage()).isEqualTo("AI output is unavailable");
                });
        server.verify();
    }

    private static AiProperties properties(String endpoint) {
        AiProperties properties = new AiProperties();
        properties.getActivityPlanning().setEndpoint(endpoint);
        properties.getActivityPlanning().setModel("test-model");
        properties.getActivityPlanning().setApiKey("test-token");
        properties.getActivityPlanning().setTimeoutSeconds(5);
        return properties;
    }
}

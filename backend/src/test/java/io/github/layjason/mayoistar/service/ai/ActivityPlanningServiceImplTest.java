package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ActivityPlanningServiceImpl 单元测试。
 *
 * <p>类职责：验证 AI 活动策划服务对成功输出、格式波动和不可解析输出的处理。
 *
 * <p>类不变量：测试使用内存 Fake 客户端，不调用真实 LLM API。
 */
class ActivityPlanningServiceImplTest {

    private FakeActivityPlanningClient client;
    private ActivityPlanningServiceImpl service;

    @BeforeEach
    void setUp() {
        client = new FakeActivityPlanningClient();
        ObjectMapper objectMapper = new ObjectMapper();
        service =
                new ActivityPlanningServiceImpl(new ActivityPlanningPromptBuilder(objectMapper), client, objectMapper);
    }

    @Test
    @DisplayName("模型返回 JSON 时应映射为成功策划结果")
    void shouldMapJsonOutputToPlanningResult() {
        client.content = """
                {
                  "title": "周末羽毛球轻运动",
                  "tags": ["运动", "羽毛球", "交友"],
                  "introduction": "适合初学者参加的周末羽毛球活动，提供轻松分组和轮换机制。",
                  "safetyNotice": "请穿着运动鞋，提前热身，如有不适及时停止。",
                  "suggestedCapacity": 16,
                  "suggestedRegistrationDeadline": "2026-07-08T12:00:00Z"
                }
                """;

        AiDtos.ActivityPlanningResult result = service.generateActivityPlan(request());

        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getTitle()).isEqualTo("周末羽毛球轻运动");
        assertThat(result.getTags()).containsExactly("运动", "羽毛球", "交友");
        assertThat(result.getSuggestedCapacity()).isEqualTo(16);
        assertThat(result.getSuggestedRegistrationDeadline()).isEqualTo("2026-07-08T12:00:00Z");
        assertThat(client.prompt).contains("羽毛球活动");
        assertThat(client.prompt).contains("适合新手");
    }

    @Test
    @DisplayName("模型返回代码块包裹 JSON 时应容错解析")
    void shouldParseJsonInsideMarkdownFence() {
        client.content = """
                ```json
                {"title":"桌游破冰夜","tags":["桌游"],"suggestedCapacity":12}
                ```
                """;

        AiDtos.ActivityPlanningResult result = service.generateActivityPlan(request());

        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getTitle()).isEqualTo("桌游破冰夜");
        assertThat(result.getTags()).containsExactly("桌游");
        assertThat(result.getSuggestedCapacity()).isEqualTo(12);
    }

    @Test
    @DisplayName("模型输出非 JSON 时应抛出 AI 输出不可用业务异常")
    void shouldThrowBusinessExceptionWhenModelOutputInvalid() {
        client.content = "这里不是 JSON";

        assertThatThrownBy(() -> service.generateActivityPlan(request()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCodes.AI_OUTPUT_UNAVAILABLE);
                    assertThat(exception.getBusinessMessage()).isEqualTo("AI output is unavailable");
                });
    }

    @Test
    @DisplayName("客户端业务异常应原样向上抛出")
    void shouldPropagateClientBusinessException() {
        client.exception = new BusinessException(ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI service is unavailable");

        assertThatThrownBy(() -> service.generateActivityPlan(request())).isSameAs(client.exception);
    }

    private AiDtos.ActivityPlanningRequest request() {
        AiDtos.ActivityPlanningRequest request = new AiDtos.ActivityPlanningRequest();
        request.setTopic("羽毛球活动");
        request.setActivityType("运动");
        request.setCity("北京");
        request.setExpectedParticipants(12);
        request.setAdditionalRequirements("适合新手");
        return request;
    }

    private static class FakeActivityPlanningClient implements ActivityPlanningClient {

        private String content = "{}";
        private String prompt;
        private BusinessException exception;

        @Override
        public String generate(String prompt) {
            this.prompt = prompt;
            if (exception != null) {
                throw exception;
            }
            return content;
        }
    }
}

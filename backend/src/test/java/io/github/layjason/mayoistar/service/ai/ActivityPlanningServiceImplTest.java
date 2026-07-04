package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.api.ai.AiDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ActivityPlanningServiceImpl 单元测试。
 *
 * <p>类职责：验证 AI 活动策划服务对成功、失败和模型输出格式波动的处理。
 *
 * <p>类不变量：测试使用内存 Fake 客户端，不调用真实 LLM API。
 */
class ActivityPlanningServiceImplTest {

    private FakeActivityPlanningClient client;
    private ActivityPlanningServiceImpl service;

    @BeforeEach
    void setUp() {
        client = new FakeActivityPlanningClient();
        service = new ActivityPlanningServiceImpl(new ActivityPlanningPromptBuilder(), client, new ObjectMapper());
    }

    @Test
    @DisplayName("模型返回 JSON 时应映射为成功策划结果")
    void shouldMapJsonOutputToPlanningResult() {
        client.result = ActivityPlanningClientResult.succeeded("""
                {
                  "title": "周末羽毛球轻运动",
                  "tags": ["运动", "羽毛球", "交友"],
                  "introduction": "适合初学者参加的周末羽毛球活动，提供轻松分组和轮换机制。",
                  "safetyNotice": "请穿着运动鞋，提前热身，如有不适及时停止。",
                  "suggestedCapacity": 16,
                  "suggestedRegistrationDeadline": "2026-07-08T12:00:00Z"
                }
                """);

        AiDtos.ActivityPlanningResult result = service.generateActivityPlan(request());

        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getTitle()).isEqualTo("周末羽毛球轻运动");
        assertThat(result.getTags()).containsExactly("运动", "羽毛球", "交友");
        assertThat(result.getSuggestedCapacity()).isEqualTo(16);
        assertThat(result.getSuggestedRegistrationDeadline()).isEqualTo("2026-07-08T12:00:00Z");
    }

    @Test
    @DisplayName("模型返回代码块包裹 JSON 时应容错解析")
    void shouldParseJsonInsideMarkdownFence() {
        client.result = ActivityPlanningClientResult.succeeded("""
                ```json
                {"title":"桌游破冰夜","tags":["桌游"],"suggestedCapacity":12}
                ```
                """);

        AiDtos.ActivityPlanningResult result = service.generateActivityPlan(request());

        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getTitle()).isEqualTo("桌游破冰夜");
        assertThat(result.getTags()).containsExactly("桌游");
        assertThat(result.getSuggestedCapacity()).isEqualTo(12);
    }

    @Test
    @DisplayName("客户端未配置时应返回 failed 而不是抛出异常")
    void shouldReturnFailedWhenClientUnavailable() {
        client.result = ActivityPlanningClientResult.failed("AI 活动策划接口尚未配置");

        AiDtos.ActivityPlanningResult result = service.generateActivityPlan(request());

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getTags()).isEmpty();
        assertThat(result.getFriendlyErrorMessage()).isEqualTo("AI 活动策划接口尚未配置");
    }

    @Test
    @DisplayName("模型输出非 JSON 时应返回友好失败结果")
    void shouldReturnFailedWhenModelOutputInvalid() {
        client.result = ActivityPlanningClientResult.succeeded("这里不是 JSON");

        AiDtos.ActivityPlanningResult result = service.generateActivityPlan(request());

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getTags()).isEmpty();
        assertThat(result.getFriendlyErrorMessage()).contains("AI 活动策划暂时不可用");
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

        private ActivityPlanningClientResult result = ActivityPlanningClientResult.failed("未设置测试结果");

        @Override
        public ActivityPlanningClientResult generate(String prompt) {
            return result;
        }
    }
}

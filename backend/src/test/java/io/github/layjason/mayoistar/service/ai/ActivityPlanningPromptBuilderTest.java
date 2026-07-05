package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ActivityPlanningPromptBuilder 单元测试。
 *
 * <p>类职责：验证活动策划请求中的字段会进入大模型提示词。
 */
class ActivityPlanningPromptBuilderTest {

    private final ActivityPlanningPromptBuilder promptBuilder = new ActivityPlanningPromptBuilder();

    @Test
    @DisplayName("应将活动策划请求字段写入提示词")
    void shouldIncludeRequestFieldsInPrompt() {
        AiDtos.ActivityPlanningRequest request = new AiDtos.ActivityPlanningRequest();
        request.setTopic("周末羽毛球");
        request.setActivityType("运动");
        request.setCity("北京");
        request.setExpectedParticipants(12);
        request.setAdditionalRequirements("适合新手，控制预算");

        String prompt = promptBuilder.build(request);

        assertThat(prompt).contains("周末羽毛球");
        assertThat(prompt).contains("运动");
        assertThat(prompt).contains("北京");
        assertThat(prompt).contains("12");
        assertThat(prompt).contains("适合新手，控制预算");
        assertThat(prompt).contains("只能输出一个 JSON 对象");
    }

    @Test
    @DisplayName("未填写的可选字段应标记为未指定")
    void shouldMarkBlankOptionalFieldsAsUnspecified() {
        AiDtos.ActivityPlanningRequest request = new AiDtos.ActivityPlanningRequest();
        request.setTopic("桌游局");
        request.setActivityType("");

        String prompt = promptBuilder.build(request);

        assertThat(prompt).contains("- 活动主题：桌游局");
        assertThat(prompt).contains("- 活动类型：未指定");
        assertThat(prompt).contains("- 城市：未指定");
        assertThat(prompt).contains("- 期望参与人数：未指定");
        assertThat(prompt).contains("- 补充要求：未指定");
    }
}

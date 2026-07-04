package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import org.springframework.stereotype.Component;

/**
 * AI 活动策划提示词构造器。
 *
 * <p>类职责：将用户输入转换为稳定的中文策划提示词，并约束模型只输出 JSON。
 *
 * <p>类不变量：提示词不包含任何模型供应商凭据或内部密钥。
 */
@Component
public class ActivityPlanningPromptBuilder {

    /**
     * 构造活动策划提示词。
     *
     * <p>前置条件：request 已通过 Controller Bean Validation，topic 非空。
     *
     * <p>后置条件：返回包含字段约束和输出格式要求的提示词。
     *
     * <p>不变量：不修改 request。
     *
     * @param request 活动策划请求
     * @return 大模型提示词
     */
    public String build(AiDtos.ActivityPlanningRequest request) {
        return String.join(
                System.lineSeparator(),
                "你是迷星群聚 MayoiStar 的活动策划助手。请根据用户输入生成一个可编辑的活动草案。",
                "",
                "用户输入：",
                "- 活动主题：" + request.getTopic(),
                "- 活动类型：" + valueOrUnspecified(request.getActivityType()),
                "- 城市：" + valueOrUnspecified(request.getCity()),
                "- 期望参与人数："
                        + (request.getExpectedParticipants() == null
                                ? "未指定"
                                : request.getExpectedParticipants().toString()),
                "- 补充要求：" + valueOrUnspecified(request.getAdditionalRequirements()),
                "",
                "输出要求：",
                "1. 只能输出一个 JSON 对象，不要使用 Markdown，不要解释。",
                "2. JSON 字段必须为：",
                "   title: string，建议活动名称；",
                "   tags: string[]，2 到 5 个中文标签；",
                "   introduction: string，80 到 180 字活动简介；",
                "   safetyNotice: string，30 到 120 字安全须知；",
                "   suggestedCapacity: number，建议人数上限；",
                "   suggestedRegistrationDeadline: string，ISO-8601 时间字符串。",
                "3. suggestedCapacity 必须为正整数；若用户给出期望人数，应围绕该人数给出合理上限。",
                "4. suggestedRegistrationDeadline 必须早于活动开始假设时间；若没有具体时间，请给出未来 7 天内的合理 ISO-8601 时间。",
                "5. 内容必须适合公开活动发布，不要生成违法、危险、歧视或露骨内容。");
    }

    private String valueOrUnspecified(String value) {
        return value == null || value.isBlank() ? "未指定" : value;
    }
}

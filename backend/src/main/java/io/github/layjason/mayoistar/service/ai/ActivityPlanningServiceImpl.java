package io.github.layjason.mayoistar.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 活动策划业务服务实现。
 *
 * <p>类职责：构造提示词、调用模型客户端、解析模型 JSON 输出并映射为 API DTO。
 *
 * <p>类不变量：模型输出必须经过 JSON 解析和字段归一化；无法解析时返回契约业务错误，不暴露供应商细节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityPlanningServiceImpl implements ActivityPlanningService {

    private final ActivityPlanningPromptBuilder promptBuilder;
    private final ActivityPlanningClient activityPlanningClient;
    private final ActivityPlanningOutputGuard outputGuard;
    private final ObjectMapper objectMapper;

    /**
     * 生成活动策划草案。
     *
     * <p>前置条件：request 非空且 topic 非空。
     *
     * <p>后置条件：模型成功且输出可解析时返回 succeeded；否则抛出 AI 输出不可用业务异常。
     *
     * <p>不变量：不保存用户输入，不记录完整 prompt 或模型原文，避免日志泄露用户内容。
     *
     * @param request 活动策划请求
     * @return 活动策划结果
     */
    @Override
    public AiDtos.ActivityPlanningResult generateActivityPlan(AiDtos.ActivityPlanningRequest request) {
        String prompt = promptBuilder.build(request);
        String content = activityPlanningClient.generate(prompt);

        try {
            AiDtos.ActivityPlanningResult result = parseResult(content);
            log.info(
                    "AI 活动策划生成成功: topicLength={}, tagCount={}",
                    request.getTopic().length(),
                    result.getTags().size());
            return result;
        } catch (RuntimeException exception) {
            log.warn(
                    "AI 活动策划输出解析失败: topicLength={}, error={}",
                    request.getTopic().length(),
                    exception.getMessage());
            throw new BusinessException(ErrorCodes.AI_OUTPUT_UNAVAILABLE, "AI output is unavailable", exception);
        }
    }

    private AiDtos.ActivityPlanningResult parseResult(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stripCodeFence(content));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI output is not valid JSON", exception);
        }
        AiDtos.ActivityPlanningResult result = new AiDtos.ActivityPlanningResult();
        result.setStatus("succeeded");
        result.setTitle(text(root, "title"));
        result.setTags(tags(root.get("tags")));
        result.setIntroduction(text(root, "introduction"));
        result.setSafetyNotice(text(root, "safetyNotice"));
        result.setSuggestedCapacity(positiveInteger(root.get("suggestedCapacity")));
        result.setSuggestedRegistrationDeadline(text(root, "suggestedRegistrationDeadline"));
        outputGuard.validate(result);
        return result;
    }

    private String stripCodeFence(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("AI output is empty");
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, lastFence).trim();
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        return node == null || node.isNull() ? null : node.asText();
    }

    private List<String> tags(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                tags.add(item.asText());
            }
        }
        return tags;
    }

    private Integer positiveInteger(JsonNode node) {
        if (node == null || !node.canConvertToInt()) {
            return null;
        }
        int value = node.asInt();
        return value > 0 ? value : null;
    }
}

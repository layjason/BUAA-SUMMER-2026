package io.github.layjason.mayoistar.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.api.ai.AiDtos;
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
 * <p>类不变量：模型输出必须经过 JSON 解析和字段归一化；无法解析时返回 failed，不抛出供应商细节给用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityPlanningServiceImpl implements ActivityPlanningService {

    private final ActivityPlanningPromptBuilder promptBuilder;
    private final ActivityPlanningClient activityPlanningClient;
    private final ObjectMapper objectMapper;

    /**
     * 生成活动策划草案。
     *
     * <p>前置条件：request 非空且 topic 非空。
     *
     * <p>后置条件：模型成功且输出可解析时返回 succeeded；否则返回 failed 与友好错误。
     *
     * <p>不变量：不保存用户输入，不记录完整 prompt 或模型原文，避免日志泄露用户内容。
     */
    @Override
    public AiDtos.ActivityPlanningResult generateActivityPlan(AiDtos.ActivityPlanningRequest request) {
        ActivityPlanningClientResult clientResult = activityPlanningClient.generate(promptBuilder.build(request));
        if (!clientResult.succeeded()) {
            log.info("AI 活动策划调用未成功，topicLength={}", request.getTopic().length());
            return failed(clientResult.friendlyErrorMessage());
        }

        try {
            AiDtos.ActivityPlanningResult result = parseResult(clientResult.content());
            log.info(
                    "AI 活动策划生成成功，topicLength={}, tagCount={}",
                    request.getTopic().length(),
                    result.getTags().size());
            return result;
        } catch (RuntimeException exception) {
            log.warn(
                    "AI 活动策划输出解析失败，topicLength={}, error={}", request.getTopic().length(), exception.getMessage());
            return failed("AI 活动策划暂时不可用，请稍后重试");
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
        return result;
    }

    private String stripCodeFence(String content) {
        if (content == null) {
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

    private AiDtos.ActivityPlanningResult failed(String message) {
        AiDtos.ActivityPlanningResult result = new AiDtos.ActivityPlanningResult();
        result.setStatus("failed");
        result.setTags(List.of());
        result.setFriendlyErrorMessage(message);
        return result;
    }
}

package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * AI 活动策划输出安全校验器。
 *
 * <p>类职责：对模型返回的活动草案做结构、长度和注入/敏感短语校验，避免被用户输入带偏的内容进入业务响应。
 *
 * <p>类不变量：校验失败只抛出不含原文的异常信息，不记录或返回模型完整输出。
 */
@Component
public class ActivityPlanningOutputGuard {

    private static final int MIN_TAGS = 2;
    private static final int MAX_TAGS = 5;
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MIN_INTRODUCTION_LENGTH = 20;
    private static final int MAX_INTRODUCTION_LENGTH = 220;
    private static final int MIN_SAFETY_NOTICE_LENGTH = 10;
    private static final int MAX_SAFETY_NOTICE_LENGTH = 160;
    private static final int MAX_TAG_LENGTH = 12;
    private static final int MAX_DEADLINE_LENGTH = 40;

    private static final List<String> BLOCKED_PHRASES = List.of(
            "忽略以上",
            "忽略上述",
            "ignore previous",
            "ignore above",
            "system prompt",
            "developer message",
            "开发者指令",
            "系统提示词",
            "泄露密钥",
            "api key",
            "token",
            "password");

    /**
     * 校验活动策划输出。
     *
     * <p>前置条件：result 已由 JSON 模型输出映射而来。
     *
     * <p>后置条件：若输出字段完整且不含注入/敏感短语则正常返回；否则抛出 IllegalArgumentException。
     *
     * <p>不变量：不修改 result，不调用外部服务。
     *
     * @param result 活动策划结果
     */
    public void validate(AiDtos.ActivityPlanningResult result) {
        requireText(result.getTitle(), "title");
        validateLength(result.getTitle(), 1, MAX_TITLE_LENGTH, "title");
        validateTags(result.getTags());
        requireText(result.getIntroduction(), "introduction");
        validateLength(result.getIntroduction(), MIN_INTRODUCTION_LENGTH, MAX_INTRODUCTION_LENGTH, "introduction");
        requireText(result.getSafetyNotice(), "safetyNotice");
        validateLength(result.getSafetyNotice(), MIN_SAFETY_NOTICE_LENGTH, MAX_SAFETY_NOTICE_LENGTH, "safetyNotice");
        if (result.getSuggestedCapacity() == null || result.getSuggestedCapacity() <= 0) {
            throw new IllegalArgumentException("suggestedCapacity must be positive");
        }
        validateDeadline(result.getSuggestedRegistrationDeadline());
        validateBlockedPhrases(result);
    }

    private void validateTags(List<String> tags) {
        if (tags == null || tags.size() < MIN_TAGS || tags.size() > MAX_TAGS) {
            throw new IllegalArgumentException("tags size is invalid");
        }
        for (String tag : tags) {
            requireText(tag, "tag");
            validateLength(tag, 1, MAX_TAG_LENGTH, "tag");
        }
    }

    private void validateDeadline(String deadline) {
        requireText(deadline, "suggestedRegistrationDeadline");
        validateLength(deadline, 1, MAX_DEADLINE_LENGTH, "suggestedRegistrationDeadline");
        try {
            OffsetDateTime.parse(deadline);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("suggestedRegistrationDeadline must be ISO-8601", exception);
        }
    }

    private void validateBlockedPhrases(AiDtos.ActivityPlanningResult result) {
        validateBlockedPhrases(result.getTitle(), "title");
        validateBlockedPhrases(result.getIntroduction(), "introduction");
        validateBlockedPhrases(result.getSafetyNotice(), "safetyNotice");
        for (String tag : result.getTags()) {
            validateBlockedPhrases(tag, "tag");
        }
    }

    private void validateBlockedPhrases(String text, String fieldName) {
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String phrase : BLOCKED_PHRASES) {
            if (normalized.contains(phrase.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(fieldName + " contains blocked phrase");
            }
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private void validateLength(String value, int minLength, int maxLength, String fieldName) {
        int length = value.codePointCount(0, value.length());
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(fieldName + " length is invalid");
        }
    }
}

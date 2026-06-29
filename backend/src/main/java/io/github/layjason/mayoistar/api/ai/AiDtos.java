package io.github.layjason.mayoistar.api.ai;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * AI 辅助接口 DTO 集合。
 *
 * <p>类职责：提供与 TypeSpec AI 接口请求/响应模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class AiDtos {

    private AiDtos() {}

    /* ========== 请求 DTO ========== */

    @Data
    public static class ActivityPlanningRequest {
        @NotNull
        private String topic;

        private String activityType;
        private String city;
        private Integer expectedParticipants;
        private String additionalRequirements;
    }

    @Data
    public static class ImageClassificationRequest {
        @NotNull
        private List<String> mediaIds;
    }

    /* ========== 响应 DTO ========== */

    @Data
    public static class ActivityPlanningResult {
        private String status;
        private String title;
        private List<String> tags;
        private String introduction;
        private String safetyNotice;
        private Integer suggestedCapacity;
        private String suggestedRegistrationDeadline;
        private String friendlyErrorMessage;
    }

    @Data
    public static class ImageClassificationItem {
        private String mediaId;
        private List<String> suggestedTags;
        private Double confidence;
    }

    @Data
    public static class ImageClassificationResult {
        private String status;
        private List<ImageClassificationItem> items;
        private String friendlyErrorMessage;
    }
}

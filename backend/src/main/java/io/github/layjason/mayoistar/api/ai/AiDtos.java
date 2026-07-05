package io.github.layjason.mayoistar.api.ai;

import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
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
        @NotBlank
        private String topic;

        private String activityType;
        private String city;

        @Positive
        private Integer expectedParticipants;

        private String additionalRequirements;
    }

    @Data
    public static class ImageClassificationRequest {
        private List<UUID> mediaIds;
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
        private UUID mediaId;
        private List<String> suggestedTags;
        private Double confidence;
    }

    @Data
    public static class ImageClassificationResult {
        private String status;
        private List<ImageClassificationItem> items;
        private String friendlyErrorMessage;
    }

    @Data
    public static class ClassifyTaskSubmitResponse {
        private UUID taskId;
        private String status;
    }

    @Data
    public static class ClassifyTaskQueryResponse {
        private String status;
        private List<ImageClassificationItem> items;
        private String errorMessage;
    }

    @Data
    public static class MediaClassificationResponse {
        private UUID mediaId;
        private List<String> suggestedTags;
        private Double confidence;
        private String classifiedAt;
    }

    @Data
    public static class ImageClassificationCompletedEvent {
        private String kind;
        private UUID taskId;
        private String status;
    }

    @Data
    public static class AiContentReviewResult {
        private String status;
        private String riskLevel;
        private ReviewStatus suggestedReviewStatus;
        private List<String> reasons;
        private String friendlyErrorMessage;
    }
}

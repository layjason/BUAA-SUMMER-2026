package io.github.layjason.mayoistar.api.activities;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

public final class ActivityDtos {

    private ActivityDtos() {}

    /* ========== 请求 DTO ========== */

    @Data
    public static class ActivityUpsertRequest {
        @NotBlank
        @Size(max = 200)
        private String title;

        @NotEmpty
        private List<String> tags;

        @NotBlank
        private String introduction;

        @NotBlank
        private String startAt;

        @NotBlank
        private String endAt;

        @NotNull
        @Valid
        private CommonDtos.LocationInfo location;

        @NotBlank
        private String safetyNotice;

        @Positive
        @Min(1)
        private Integer capacity;

        @NotBlank
        private String registrationDeadline;

        @PositiveOrZero
        private BigDecimal feeAmount;

        @Size(max = 500)
        private String feeDescription;

        @Min(0)
        private Integer minAge;

        private List<String> imageIds;
    }

    @Data
    public static class ActivityDraftUpsertRequest {
        private String title;
        private List<String> tags;
        private String introduction;
        private String startAt;
        private String endAt;

        @Valid
        private CommonDtos.LocationInfo location;

        private String safetyNotice;
        private Integer capacity;
        private String registrationDeadline;
        private BigDecimal feeAmount;
        private String feeDescription;
        private Integer minAge;
        private List<String> imageIds;
    }

    @Data
    public static class RegisterActivityRequest {
        private String participantNote;

        @AssertTrue
        private Boolean acceptedSafetyNotice;
    }

    @Data
    public static class WaitingConfirmationRequest {
        @NotNull
        private Boolean confirmed;
    }

    @Data
    public static class CheckInRequest {
        @NotBlank
        private String qrCodeToken;

        private CommonDtos.GeoPoint currentLocation;
    }

    @Data
    public static class ActivitySummaryPostRequest {
        @NotBlank
        @Size(max = 200)
        private String title;

        @NotBlank
        private String content;

        @NotEmpty
        private List<String> imageIds;

        @NotEmpty
        private List<CommonDtos.ImageTagConfirmation> confirmedImageTags;
    }

    @Data
    public static class ActivityReviewRequest {
        @Min(1)
        @Max(5)
        private Integer rating;

        private String content;
        private List<String> tags;
    }

    /* ========== 响应 DTO ========== */

    @Data
    public static class ActivitySummary {
        private String activityId;
        private String title;
        private List<String> tags;
        private String startAt;
        private String endAt;
        private CommonDtos.LocationInfo location;
        private CommonDtos.MediaFile coverImage;
        private BigDecimal feeAmount;
        private ActivityReviewStatus reviewStatus;
        private ActivityRuntimeStatus runtimeStatus;
        private Integer registeredCount;
        private Integer capacity;
    }

    @Data
    public static class ReviewRecord {
        private String reviewId;
        private String reviewerId;
        private ReviewStatus result;
        private String reason;
        private String reviewedAt;
    }

    @Data
    public static class ActivityDetail {
        private String activityId;
        private String title;
        private List<String> tags;
        private String startAt;
        private String endAt;
        private CommonDtos.LocationInfo location;
        private CommonDtos.MediaFile coverImage;
        private BigDecimal feeAmount;
        private ActivityReviewStatus reviewStatus;
        private ActivityRuntimeStatus runtimeStatus;
        private Integer registeredCount;
        private Integer capacity;
        private String introduction;
        private String safetyNotice;
        private String registrationDeadline;
        private String organizerId;
        private String organizerName;
        private List<CommonDtos.MediaFile> images;
        private Integer waitingCount;
        private Boolean manualReviewRequired;
        private List<ReviewRecord> reviewRecords;
    }

    @Data
    public static class ActivityDraftDetail {
        private String activityId;
        private String title;
        private List<String> tags;
        private String introduction;
        private String startAt;
        private String endAt;
        private CommonDtos.LocationInfo location;
        private String safetyNotice;
        private Integer capacity;
        private String registrationDeadline;
        private BigDecimal feeAmount;
        private String feeDescription;
        private Integer minAge;
        private List<CommonDtos.MediaFile> images;
        private ActivityReviewStatus reviewStatus;
        private String updatedAt;
        private String createdAt;
    }

    @Data
    public static class ActivityDraftSummary {
        private String activityId;
        private String title;
        private ActivityReviewStatus reviewStatus;
        private String updatedAt;
        private String createdAt;
    }

    @Data
    public static class ActivityTemplate {
        private String templateId;
        private String name;
        private String activityType;
        private List<String> defaultTags;
        private String defaultIntroduction;
        private String defaultSafetyNotice;
        private Integer defaultCapacity;
        private CommonDtos.MediaFile defaultCoverImage;
    }

    @Data
    public static class ActivityMapPoint {
        private String activityId;
        private String title;
        private CommonDtos.GeoPoint point;
        private ActivityRuntimeStatus runtimeStatus;
        private String startAt;
    }

    @Data
    public static class RegistrationResult {
        private String registrationId;
        private String activityId;
        private RegistrationStatus status;
        private Integer waitingRank;
        private String confirmationDeadline;
    }

    @Data
    public static class ActivityParticipant {
        private String registrationId;
        private String userId;
        private String nickname;
        private CommonDtos.MediaFile avatar;
        private RegistrationStatus registrationStatus;
        private Integer waitingRank;
        private String registeredAt;
        private String checkedInAt;
    }

    @Data
    public static class ActivityParticipationState {
        private Boolean canRegister;
        private RegistrationStatus status;
        private Integer waitingRank;
        private String confirmationDeadline;
        private Boolean canCancelRegistration;
        private Boolean canConfirmWaitingSeat;
        private Boolean canCheckIn;
    }

    @Data
    public static class CheckInQrCode {
        private String activityId;
        private String qrCodeToken;
        private String expiresAt;
    }

    @Data
    public static class CheckInRecord {
        private String registrationId;
        private String userId;
        private String nickname;
        private RegistrationStatus registrationStatus;
        private String checkedInAt;
    }

    @Data
    public static class ActivitySummaryPost {
        private String summaryId;
        private String activityId;
        private String title;
        private String content;
        private List<CommonDtos.MediaFile> images;
        private List<CommonDtos.ImageTagConfirmation> imageTags;
        private String createdAt;
    }

    @Data
    public static class ActivityReview {
        private String reviewId;
        private String activityId;
        private String userId;
        private Integer rating;
        private String content;
        private List<String> tags;
        private String createdAt;
    }
}

package io.github.layjason.mayoistar.api.activities;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

public final class ActivityDtos {

    private ActivityDtos() {}

    /* ========== 请求 DTO ========== */

    @Data
    public static class ActivityUpsertRequest {
        @NotNull
        private String title;

        @NotNull
        private List<String> tags;

        @NotNull
        private String introduction;

        @NotNull
        private String startAt;

        @NotNull
        private String endAt;

        @NotNull
        private Object location;

        @NotNull
        private String safetyNotice;

        @NotNull
        private Integer capacity;

        @NotNull
        private String registrationDeadline;

        private Double feeAmount;
        private String feeDescription;
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
        private Object location;
        private String safetyNotice;
        private Integer capacity;
        private String registrationDeadline;
        private Double feeAmount;
        private String feeDescription;
        private Integer minAge;
        private List<String> imageIds;
    }

    @Data
    public static class RegisterActivityRequest {
        private String participantNote;

        @NotNull
        private Boolean acceptedSafetyNotice;
    }

    @Data
    public static class WaitingConfirmationRequest {
        @NotNull
        private Boolean confirmed;
    }

    @Data
    public static class CheckInRequest {
        @NotNull
        private String qrCodeToken;

        private Object currentLocation;
    }

    @Data
    public static class ActivitySummaryPostRequest {
        @NotNull
        private String title;

        @NotNull
        private String content;

        @NotNull
        private List<String> imageIds;

        @NotNull
        private List<Object> confirmedImageTags;
    }

    @Data
    public static class ActivityReviewRequest {
        @NotNull
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
        private Double feeAmount;
        private String reviewStatus;
        private String runtimeStatus;
        private Integer registeredCount;
        private Integer capacity;
    }

    @Data
    public static class ReviewRecord {
        private String reviewerId;
        private String reviewerName;
        private String status;
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
        private Double feeAmount;
        private String reviewStatus;
        private String runtimeStatus;
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
        private Double feeAmount;
        private String feeDescription;
        private Integer minAge;
        private List<CommonDtos.MediaFile> images;
        private String reviewStatus;
        private String updatedAt;
        private String createdAt;
    }

    @Data
    public static class ActivityDraftSummary {
        private String activityId;
        private String title;
        private String reviewStatus;
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
        private String runtimeStatus;
        private String startAt;
    }

    @Data
    public static class RegistrationResult {
        private String registrationId;
        private String activityId;
        private String status;
        private Integer waitingRank;
        private String confirmationDeadline;
    }

    @Data
    public static class ActivityParticipant {
        private String registrationId;
        private String userId;
        private String nickname;
        private CommonDtos.MediaFile avatar;
        private String registrationStatus;
        private Integer waitingRank;
        private String registeredAt;
        private String checkedInAt;
    }

    @Data
    public static class ActivityParticipationState {
        private Boolean canRegister;
        private String status;
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
        private String registrationStatus;
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

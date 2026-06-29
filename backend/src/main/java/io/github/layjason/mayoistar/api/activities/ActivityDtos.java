package io.github.layjason.mayoistar.api.activities;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

public final class ActivityDtos {

    private ActivityDtos() {}

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
}

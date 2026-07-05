package io.github.layjason.mayoistar.api.admin;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.admin.AdminModerationAction;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.TeamJoinMode;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 后台管理接口 DTO 集合。
 *
 * <p>类职责：提供与 TypeSpec 后台接口请求/响应模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class AdminDtos {

    private AdminDtos() {}

    /* ========== 请求 DTO ========== */

    @Data
    public static class AdminLoginRequest {
        @NotBlank
        @Size(max = 50)
        private String username;

        @NotBlank
        private String password;
    }

    @Data
    public static class AdminChangePasswordRequest {
        @NotBlank
        private String oldPassword;

        @NotBlank
        @Size(min = 8)
        private String newPassword;
    }

    @Data
    public static class BanUserRequest {
        @NotBlank
        private String reason;

        @NotBlank
        private String bannedUntil;
    }

    @Data
    public static class MerchantReviewRequest {
        @NotNull
        private Boolean approved;

        private String reason;
    }

    @Data
    public static class ActivityModerationRequest {
        @NotBlank
        private String reason;
    }

    @Data
    public static class ReviewDecisionRequest {
        @NotNull
        private ReviewStatus result;

        private String reason;
    }

    @Data
    public static class ReportDecisionRequest {
        @NotNull
        private ReportStatus status;

        @NotBlank
        private String handlingNote;
    }

    @Data
    public static class TeamModerationRequest {
        @NotBlank
        private String reason;
    }

    /* ========== 响应 DTO ========== */

    @Data
    public static class AdminLoginResponse {
        @NotNull
        private String userId;

        @NotNull
        private IdentityDtos.TokenPair tokens;
    }

    @Data
    public static class AdminUserSummary {
        @NotNull
        private String userId;

        @NotNull
        private String email;

        private String nickname;

        @NotNull
        private UserKind kind;

        @NotNull
        private AccountStatus status;

        private QualificationStatus qualificationStatus;

        @NotNull
        private Integer activityCount;

        @NotNull
        private Integer teamCount;

        @NotNull
        private String createdAt;
    }

    @Data
    public static class AdminUserDetail {
        private String userId;
        private String email;
        private String nickname;
        private UserKind kind;
        private AccountStatus status;
        private QualificationStatus qualificationStatus;
        private Integer activityCount;
        private Integer teamCount;
        private String createdAt;
        private AdminBanInfo currentBanInfo;
    }

    @Data
    public static class AdminBanInfo {
        private String reason;
        private String bannedUntil;
        private String createdAt;
        private String operatorId;
    }

    @Data
    public static class AdminModerationRecord {
        private String recordId;
        private AdminModerationAction action;
        private String reason;
        private String operatorId;
        private String createdAt;
    }

    @Data
    public static class AdminTeamDetail {
        private String teamId;
        private String name;
        private List<String> tags;
        private TeamJoinMode joinMode;
        private Integer capacity;
        private Integer memberCount;
        private String description;
        private CommonDtos.MediaFile avatar;
        private TeamStatus status;
        private String creatorId;
        private String leaderId;
        private String chatId;
        private List<AdminModerationRecord> moderationRecords;
    }
}

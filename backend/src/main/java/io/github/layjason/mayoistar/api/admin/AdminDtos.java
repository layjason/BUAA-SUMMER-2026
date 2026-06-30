package io.github.layjason.mayoistar.api.admin;

import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import jakarta.validation.constraints.NotNull;
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
        @NotNull
        private String username;

        @NotNull
        private String password;
    }

    @Data
    public static class AdminChangePasswordRequest {
        @NotNull
        private String oldPassword;

        @NotNull
        private String newPassword;
    }

    @Data
    public static class BanUserRequest {
        @NotNull
        private String reason;

        @NotNull
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
        @NotNull
        private String reason;
    }

    @Data
    public static class ReviewDecisionRequest {
        @NotNull
        private String result;

        private String reason;
    }

    @Data
    public static class UserReportDecisionRequest {
        @NotNull
        private String status;

        @NotNull
        private String handlingNote;
    }

    @Data
    public static class TeamModerationRequest {
        @NotNull
        private String reason;
    }

    /* ========== 响应 DTO ========== */

    @Data
    public static class AdminLoginResponse {
        private String userId;
        private IdentityDtos.TokenPair tokens;
    }

    @Data
    public static class AdminUserSummary {
        private String userId;
        private String email;
        private String nickname;
        private UserKind kind;
        private AccountStatus status;
        private QualificationStatus qualificationStatus;
        private Integer activityCount;
        private Integer teamCount;
        private String createdAt;
    }
}

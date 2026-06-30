package io.github.layjason.mayoistar.api.identity;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 身份与资料接口 DTO 集合。
 *
 * <p>类职责：提供与 TypeSpec 身份接口请求/响应模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class IdentityDtos {

    private IdentityDtos() {}

    /* ========== 请求 DTO ========== */

    @Data
    public static class EmailPasswordRequest {
        @NotNull
        private String email;

        @NotNull
        private String password;
    }

    @Data
    public static class PersonalRegisterRequest {
        @NotNull
        private String email;

        @NotNull
        private String password;

        @NotNull
        private String nickname;
    }

    @Data
    public static class MerchantRegisterRequest {
        @NotNull
        private String email;

        @NotNull
        private String password;

        @NotNull
        private String nickname;
    }

    @Data
    public static class RefreshTokenRequest {
        @NotNull
        private String refreshToken;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotNull
        private String oldPassword;

        @NotNull
        private String newPassword;
    }

    @Data
    public static class PasswordResetEmailRequest {
        @NotNull
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotNull
        private String token;

        @NotNull
        private String newPassword;
    }

    @Data
    public static class AccountActivationRequest {
        @NotNull
        private String token;
    }

    @Data
    public static class ResendActivationEmailRequest {
        @NotNull
        private String email;
    }

    @Data
    public static class UpdatePersonalProfileRequest {
        private String avatarMediaId;
        private String nickname;
        private String gender;
        private String birthday;
        private String signature;
        private List<String> interestTags;
    }

    @Data
    public static class UpdateMerchantProfileRequest {
        private String merchantName;
        private String nickname;
        private String avatarMediaId;
        private List<String> interestedActivityFields;
    }

    @Data
    public static class QualificationSubmitRequest {
        @NotNull
        private List<String> licenseMediaIds;
    }

    /* ========== 响应 DTO ========== */

    @Data
    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private String expiresAt;
    }

    @Data
    public static class LoginResult {
        private String userId;
        private String kind;
        private String accountStatus;
        private TokenPair tokens;
    }

    @Data
    public static class PublicUserProfile {
        private String userId;
        private String nickname;
        private CommonDtos.MediaFile avatar;
        private String gender;
        private String birthday;
        private String signature;
        private List<String> interestTags;
        private Integer reputationScore;
        private String kind;
    }

    @Data
    public static class QualificationDetail {
        private String status;
        private String submittedAt;
        private String reviewedAt;
        private String rejectReason;
        private List<String> licenseImageUrls;
    }

    @Data
    public static class MerchantProfile {
        private String userId;
        private String merchantName;
        private String nickname;
        private CommonDtos.MediaFile avatar;
        private List<String> interestedActivityFields;
        private String accountStatus;
        private String qualificationStatus;
        private QualificationDetail qualification;
    }

    @Data
    public static class InterestTagItem {
        private String name;
    }

    @Data
    public static class NicknameAvailability {
        private String nickname;
        private Boolean available;
    }
}

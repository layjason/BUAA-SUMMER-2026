package io.github.layjason.mayoistar.api.identity;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.Gender;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
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
        @NotBlank
        @Email
        @Size(max = 255)
        private String email;

        @NotBlank
        @Size(min = 8)
        private String password;
    }

    @Data
    public static class PersonalRegisterRequest {
        @NotBlank
        @Email
        @Size(max = 255)
        private String email;

        @NotBlank
        @Size(min = 8)
        private String password;

        @NotBlank
        @Size(max = 50)
        private String nickname;
    }

    @Data
    public static class MerchantRegisterRequest {
        @NotBlank
        @Email
        @Size(max = 255)
        private String email;

        @NotBlank
        @Size(min = 8)
        private String password;

        @NotBlank
        @Size(max = 50)
        private String nickname;
    }

    @Data
    public static class RefreshTokenRequest {
        @NotBlank
        private String refreshToken;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String oldPassword;

        @NotBlank
        @Size(min = 8)
        private String newPassword;
    }

    @Data
    public static class PasswordResetEmailRequest {
        @NotBlank
        @Email
        @Size(max = 255)
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String token;

        @NotBlank
        @Size(min = 8)
        private String newPassword;
    }

    @Data
    public static class AccountActivationRequest {
        @NotBlank
        private String token;
    }

    @Data
    public static class ResendActivationEmailRequest {
        @NotBlank
        @Email
        @Size(max = 255)
        private String email;
    }

    @Data
    public static class UpdatePersonalProfileRequest {
        private UUID avatarMediaId;

        @Size(max = 50)
        private String nickname;

        private Gender gender;

        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        private String birthday;

        @Size(max = 500)
        private String signature;

        private List<String> interestTags;
    }

    @Data
    public static class UpdateMerchantProfileRequest {
        @Size(max = 100)
        private String merchantName;

        @Size(max = 50)
        private String nickname;

        private UUID avatarMediaId;

        private List<String> interestedActivityFields;
    }

    @Data
    public static class QualificationSubmitRequest {
        @NotEmpty
        private List<UUID> licenseMediaIds;
    }

    /* ========== 响应 DTO ========== */

    @Data
    public static class TokenPair {
        @NotNull
        private String accessToken;

        @NotNull
        private String refreshToken;

        @NotNull
        private String expiresAt;
    }

    @Data
    public static class LoginResult {
        @NotNull
        private String userId;

        @NotNull
        private UserKind kind;

        @NotNull
        private AccountStatus accountStatus;

        @NotNull
        private TokenPair tokens;
    }

    @Data
    public static class PublicUserProfile {
        @NotNull
        private String userId;

        @NotNull
        private String nickname;

        private CommonDtos.MediaFile avatar;
        private Gender gender;
        private String birthday;
        private String signature;

        @NotNull
        private List<String> interestTags;

        @NotNull
        private Integer reputationScore;

        @NotNull
        private UserKind kind;
    }

    @Data
    public static class QualificationDetail {
        @NotNull
        private QualificationStatus status;

        private String submittedAt;
        private String reviewedAt;
        private String rejectReason;
        private List<String> licenseImageUrls;
    }

    @Data
    public static class MerchantProfile {
        @NotNull
        private String userId;

        @NotNull
        private String merchantName;

        @NotNull
        private String nickname;

        private CommonDtos.MediaFile avatar;

        @NotNull
        private List<String> interestedActivityFields;

        @NotNull
        private AccountStatus accountStatus;

        @NotNull
        private QualificationStatus qualificationStatus;

        private QualificationDetail qualification;
    }

    @Data
    public static class InterestTagItem {
        @NotNull
        private String name;
    }

    @Data
    public static class NicknameAvailability {
        @NotNull
        private String nickname;

        @NotNull
        private Boolean available;
    }
}

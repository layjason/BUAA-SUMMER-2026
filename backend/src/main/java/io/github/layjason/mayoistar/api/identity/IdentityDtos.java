package io.github.layjason.mayoistar.api.identity;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 身份与资料接口 DTO 占位集合。
 *
 * <p>类职责：提供与 TypeSpec 身份接口请求模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class IdentityDtos {

    /**
     * 阻止实例化 DTO 命名空间类。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：外部无法创建该工具型容器类实例。
     *
     * <p>不变量：该构造函数不创建任何 DTO 对象。
     */
    private IdentityDtos() {}

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
        private String merchantNickname;
        private String avatarMediaId;
        private List<String> interestedActivityFields;
    }
}

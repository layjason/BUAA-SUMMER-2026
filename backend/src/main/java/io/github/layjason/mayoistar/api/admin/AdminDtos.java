package io.github.layjason.mayoistar.api.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台管理接口 DTO 占位集合。
 *
 * <p>类职责：提供与 TypeSpec 后台接口请求模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class AdminDtos {

    /**
     * 阻止实例化 DTO 命名空间类。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：外部无法创建该工具型容器类实例。
     *
     * <p>不变量：该构造函数不创建任何 DTO 对象。
     */
    private AdminDtos() {}

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
}

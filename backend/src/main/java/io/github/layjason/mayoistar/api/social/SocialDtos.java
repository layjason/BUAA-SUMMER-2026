package io.github.layjason.mayoistar.api.social;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 好友社群接口 DTO 占位集合。
 *
 * <p>类职责：提供与 TypeSpec 社交接口请求模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class SocialDtos {

    /**
     * 阻止实例化 DTO 命名空间类。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：外部无法创建该工具型容器类实例。
     *
     * <p>不变量：该构造函数不创建任何 DTO 对象。
     */
    private SocialDtos() {}

    @Data
    public static class FriendRequestCreate {
        @NotNull
        private String targetUserId;

        @NotNull
        private String source;

        private String message;
    }

    @Data
    public static class FriendRequestDecision {
        @NotNull
        private Boolean accepted;
    }

    @Data
    public static class UserReportCreateRequest {
        @NotNull
        private String targetUserId;

        @NotNull
        private String reason;
    }

    @Data
    public static class FriendRemarkUpdate {
        private String remark;
        private List<String> groupTags;
    }

    @Data
    public static class TeamCreateRequest {
        @NotNull
        private String name;

        @NotNull
        private List<String> tags;

        @NotNull
        private String joinMode;

        @NotNull
        private Integer capacity;

        private String description;
        private String avatarMediaId;
    }

    @Data
    public static class JoinTeamRequest {
        private String message;
    }

    @Data
    public static class TeamJoinRequestDecision {
        @NotNull
        private Boolean accepted;
    }

    @Data
    public static class TeamMemberRoleUpdate {
        @NotNull
        private String role;
    }
}

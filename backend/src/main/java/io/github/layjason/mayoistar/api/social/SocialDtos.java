package io.github.layjason.mayoistar.api.social;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import io.github.layjason.mayoistar.entity.social.FriendshipSource;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.TeamJoinMode;
import io.github.layjason.mayoistar.entity.social.TeamJoinRequestStatus;
import io.github.layjason.mayoistar.entity.social.TeamMemberRole;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 好友社群接口 DTO 集合。
 *
 * <p>类职责：提供与 TypeSpec 社交接口请求/响应模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class SocialDtos {

    private SocialDtos() {}

    /* ========== 请求 DTO ========== */

    @Data
    public static class FriendRequestCreate {
        @NotNull
        private String targetUserId;

        @NotNull
        private FriendRequestSource source;

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
        private TeamJoinMode joinMode;

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
        private TeamMemberRole role;
    }

    /* ========== 响应 DTO ========== */

    @Data
    public static class FollowRelation {
        private String targetUserId;
        private Boolean following;
        private Boolean mutual;
        private Boolean friendshipCreated;
    }

    @Data
    public static class FriendRequest {
        private String requestId;
        private String requesterId;
        private String targetUserId;
        private FriendRequestSource source;
        private String message;
        private FriendRequestStatus status;
        private String createdAt;
    }

    @Data
    public static class FriendItem {
        private String userId;
        private String nickname;
        private CommonDtos.MediaFile avatar;
        private String remark;
        private List<String> groupTags;
        private FriendshipSource source;
    }

    @Data
    public static class BlacklistItem {
        private String userId;
        private String nickname;
        private CommonDtos.MediaFile avatar;
        private String blockedAt;
    }

    @Data
    public static class FollowItem {
        private String userId;
        private String nickname;
        private CommonDtos.MediaFile avatar;
        private String followedAt;
        private Boolean mutual;
    }

    @Data
    public static class UserReport {
        private String reportId;
        private String reporterUserId;
        private String targetUserId;
        private String reason;
        private ReportStatus status;
        private String handlingNote;
        private String createdAt;
        private String handledAt;
    }

    @Data
    public static class TeamProfile {
        private String teamId;
        private String name;
        private List<String> tags;
        private TeamJoinMode joinMode;
        private Integer capacity;
        private Integer memberCount;
        private String description;
        private CommonDtos.MediaFile avatar;
        private TeamStatus status;
        private String leaderId;
        private String chatId;
    }

    @Data
    public static class TeamJoinRequest {
        private String requestId;
        private String teamId;
        private String userId;
        private String message;
        private TeamJoinRequestStatus status;
        private String createdAt;
    }

    @Data
    public static class TeamMember {
        private String userId;
        private String nickname;
        private CommonDtos.MediaFile avatar;
        private TeamMemberRole role;
        private Integer points;
        private String joinedAt;
    }

    @Data
    public static class TeamPointRankItem {
        private Integer rank;
        private String userId;
        private String nickname;
        private Integer points;
    }
}

package io.github.layjason.mayoistar.api.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * API 契约骨架默认响应工厂。
 *
 * <p>类职责：为尚未接入业务服务的 Controller 生成符合 OpenAPI 响应 Schema 的占位数据。
 *
 * <p>类不变量：所有 JSON 占位响应均使用统一成功包装，不访问数据库、不调用外部服务。
 */
@Component
public class DefaultApiResponseFactory {

    private static final String NOW = "2026-06-29T08:00:00Z";
    private static final String DATE = "2026-06-29";

    /**
     * 根据请求方法和路径创建统一成功响应。
     *
     * <p>前置条件：method 与 path 来自 Spring MVC 已匹配到领域 Controller 的请求。
     *
     * <p>后置条件：返回 HTTP 200，响应体满足对应 TypeSpec 成功响应的基本结构。
     *
     * <p>不变量：该方法只根据路径选择静态占位数据，不读取或修改业务状态。
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @return 统一成功响应
     */
    public ResponseEntity<ApiResponse<Object>> success(String method, String path) {
        return ResponseEntity.ok(ApiResponse.success(data(method, path)));
    }

    /**
     * 选择接口响应数据。
     *
     * <p>前置条件：method 与 path 来自已匹配的 API 请求。
     *
     * <p>后置条件：返回符合成功响应 data 字段的占位对象。
     *
     * <p>不变量：该方法不访问数据库，不修改外部状态。
     */
    private Object data(String method, String path) {
        if (path.contains("/media/") || path.endsWith("/media/avatar") || path.endsWith("/media/license")) {
            return media("media-placeholder", "activityImage");
        }
        if (path.endsWith("/auth/login")) {
            return loginResult();
        }
        if (path.endsWith("/auth/refresh")) {
            return tokenPair();
        }
        if (path.endsWith("/nicknames/availability")) {
            return obj("nickname", "MayoiStar", "available", true);
        }
        if (path.endsWith("/interest-tags") || path.endsWith("/map")) {
            return List.of();
        }
        if (path.endsWith("/me/profile") || path.contains("/profiles/")) {
            return publicUserProfile();
        }
        if (path.endsWith("/me/merchant-profile") || path.contains("/merchants/")) {
            return merchantProfile();
        }
        if (path.contains("/activity-plans")) {
            return obj(
                    "status",
                    "succeeded",
                    "tags",
                    List.of(),
                    "title",
                    "默认活动策划",
                    "introduction",
                    "契约占位活动简介",
                    "safetyNotice",
                    "契约占位安全须知",
                    "suggestedCapacity",
                    20,
                    "suggestedRegistrationDeadline",
                    NOW);
        }
        if (path.contains("/image-classifications")) {
            return obj("status", "succeeded", "items", List.of());
        }
        if (isEmptyDataPath(method, path)) {
            return new EmptyData();
        }
        if (isPagePath(method, path)) {
            return PageResult.empty();
        }
        return objectData(method, path);
    }

    /**
     * 为非分页、非媒体的对象响应选择占位数据。
     *
     * <p>前置条件：调用方已排除媒体上传、分页和空对象响应。
     *
     * <p>后置条件：返回与请求路径对应的对象、数组或空对象。
     *
     * <p>不变量：该方法仅根据路径字符串分派。
     */
    private Object objectData(String method, String path) {
        if (path.contains("/drafts") || path.endsWith("/clone") || path.endsWith("/submit")) {
            return path.endsWith("/submit") ? activityDetail() : activityDraftDetail();
        }
        if (path.contains("/participation-state")) {
            return obj(
                    "canRegister",
                    true,
                    "canCancelRegistration",
                    false,
                    "canConfirmWaitingSeat",
                    false,
                    "canCheckIn",
                    false);
        }
        if (path.endsWith("/registrations")
                || path.endsWith("/registrations/cancel")
                || path.endsWith("/waiting-confirmations")) {
            return obj(
                    "registrationId",
                    "registration-placeholder",
                    "activityId",
                    "activity-placeholder",
                    "status",
                    "registered");
        }
        if (path.endsWith("/check-in-qrcode")) {
            return obj("activityId", "activity-placeholder", "qrCodeToken", "qr-placeholder", "expiresAt", NOW);
        }
        if (path.endsWith("/check-ins")) {
            return checkInRecord();
        }
        if (path.endsWith("/summaries")) {
            return obj(
                    "summaryId",
                    "summary-placeholder",
                    "activityId",
                    "activity-placeholder",
                    "title",
                    "默认总结",
                    "content",
                    "默认总结内容",
                    "images",
                    List.of(),
                    "imageTags",
                    List.of(),
                    "createdAt",
                    NOW);
        }
        if (path.endsWith("/reviews")) {
            return obj(
                    "reviewId",
                    "review-placeholder",
                    "activityId",
                    "activity-placeholder",
                    "userId",
                    "user-placeholder",
                    "rating",
                    5,
                    "tags",
                    List.of(),
                    "createdAt",
                    NOW);
        }
        if (path.contains("/conversations") && path.endsWith("/messages")) {
            return chatMessage();
        }
        if (path.contains("/album-images")) {
            return media("media-placeholder", "teamAlbum");
        }
        if (path.contains("/files")) {
            return media("media-placeholder", "teamFile");
        }
        if (path.contains("/messages/read") || path.endsWith("/forward")) {
            return List.of();
        }
        if (path.endsWith("/recall")) {
            return chatMessage();
        }
        if (path.endsWith("/announcements") || path.endsWith("/read")) {
            return teamAnnouncement();
        }
        if (path.endsWith("/polls") || path.endsWith("/votes")) {
            return teamPoll();
        }
        if (path.endsWith("/ws/messages")) {
            return chatRealtimeEvent();
        }
        if (path.contains("/activities/")) {
            return activityDetail();
        }
        if (path.endsWith("/follows/target") || path.contains("/follows/")) {
            return followRelation();
        }
        if (path.endsWith("/friend-requests") || path.contains("/friend-requests/")) {
            return friendRequest();
        }
        if (path.contains("/friends/")) {
            return method.equals("DELETE") ? new EmptyData() : friendItem();
        }
        if (path.endsWith("/user-reports") || path.contains("/user-reports/")) {
            return userReport();
        }
        if (path.contains("/users/")) {
            return adminUserSummary();
        }
        if (path.endsWith("/teams") || path.matches(".*/teams/[^/]+$") || path.contains("/teams/")) {
            return teamData(path);
        }
        return new EmptyData();
    }

    /**
     * 选择小队相关接口占位数据。
     *
     * <p>前置条件：path 位于小队相关 API 路径下。
     *
     * <p>后置条件：返回小队、成员、申请、活动或分页占位数据。
     *
     * <p>不变量：该方法不校验真实小队权限。
     */
    private Object teamData(String path) {
        if (path.endsWith("/join") || path.contains("/join-requests/")) {
            return teamJoinRequest();
        }
        if (path.endsWith("/members") || path.endsWith("/role")) {
            return teamMember();
        }
        if (path.endsWith("/points")) {
            return PageResult.empty();
        }
        if (path.contains("/activities/")) {
            return activityDetail();
        }
        if (path.endsWith("/activities")) {
            return activityDetail();
        }
        return teamProfile();
    }

    /**
     * 判断接口是否返回空对象。
     *
     * <p>前置条件：method 与 path 非空。
     *
     * <p>后置条件：返回 true 表示成功响应 data 应为 EmptyData。
     *
     * <p>不变量：判断规则与当前 OpenAPI 成功响应保持一致。
     */
    private boolean isEmptyDataPath(String method, String path) {
        return path.endsWith("/auth/logout")
                || path.endsWith("/auth/activate")
                || path.endsWith("/auth/activation-email")
                || path.endsWith("/auth/password-reset-email")
                || path.endsWith("/auth/password-reset")
                || path.endsWith("/me/password")
                || path.endsWith("/me/merchant-qualification")
                || path.endsWith("/register/personal")
                || path.endsWith("/register/merchant")
                || path.endsWith("/blacklist/target")
                || (path.contains("/blacklist/") && (method.equals("POST") || method.equals("DELETE")))
                || (method.equals("DELETE") && path.contains("/friends/"))
                || path.endsWith("/leave")
                || (method.equals("DELETE") && path.contains("/album-images"))
                || (method.equals("DELETE") && path.contains("/files"));
    }

    /**
     * 判断接口是否返回分页对象。
     *
     * <p>前置条件：method 与 path 非空。
     *
     * <p>后置条件：返回 true 表示成功响应 data 应为 PageResult。
     *
     * <p>不变量：只有 GET 列表类接口被识别为分页响应。
     */
    private boolean isPagePath(String method, String path) {
        return method.equals("GET")
                && !path.endsWith("/ws/messages")
                && (path.endsWith("/templates")
                        || path.endsWith("/drafts")
                        || path.endsWith("/feed")
                        || path.endsWith("/search")
                        || path.endsWith("/mine")
                        || path.endsWith("/participants")
                        || path.endsWith("/check-ins")
                        || path.endsWith("/users")
                        || path.endsWith("/activities")
                        || path.endsWith("/teams")
                        || path.endsWith("/user-reports")
                        || path.endsWith("/conversations")
                        || path.endsWith("/messages")
                        || path.endsWith("/album-images")
                        || path.endsWith("/files")
                        || path.endsWith("/blacklist")
                        || path.endsWith("/followers")
                        || path.endsWith("/follows")
                        || path.endsWith("/friend-requests/sent")
                        || path.endsWith("/friend-requests/received")
                        || path.endsWith("/friends")
                        || path.endsWith("/join-requests")
                        || path.endsWith("/members")
                        || path.endsWith("/points"));
    }

    /**
     * 创建登录响应占位数据。
     *
     * <p>前置条件：调用方需要 LoginResult 结构。
     *
     * <p>后置条件：返回包含用户、账号状态与令牌对的 Map。
     *
     * <p>不变量：不会签发真实 JWT。
     */
    private Map<String, Object> loginResult() {
        return obj("userId", "user-placeholder", "kind", "personal", "accountStatus", "active", "tokens", tokenPair());
    }

    /**
     * 创建令牌对占位数据。
     *
     * <p>前置条件：调用方需要 TokenPair 结构。
     *
     * <p>后置条件：返回 accessToken、refreshToken 和 expiresAt 字段。
     *
     * <p>不变量：令牌值仅为静态占位字符串。
     */
    private Map<String, Object> tokenPair() {
        return obj(
                "accessToken",
                "access-token-placeholder",
                "refreshToken",
                "refresh-token-placeholder",
                "expiresAt",
                NOW);
    }

    /**
     * 创建公开用户资料占位数据。
     *
     * <p>前置条件：调用方需要 PublicUserProfile 结构。
     *
     * <p>后置条件：返回包含必填资料字段的 Map。
     *
     * <p>不变量：不读取真实用户资料。
     */
    private Map<String, Object> publicUserProfile() {
        return obj(
                "userId",
                "user-placeholder",
                "nickname",
                "MayoiStar",
                "interestTags",
                List.of(),
                "reputationScore",
                100,
                "kind",
                "personal");
    }

    /**
     * 创建商家资料占位数据。
     *
     * <p>前置条件：调用方需要 MerchantProfile 结构。
     *
     * <p>后置条件：返回包含商家必填字段的 Map。
     *
     * <p>不变量：不读取真实资质记录。
     */
    private Map<String, Object> merchantProfile() {
        return obj(
                "userId",
                "merchant-placeholder",
                "merchantName",
                "默认商家",
                "merchantNickname",
                "默认商家昵称",
                "interestedActivityFields",
                List.of(),
                "accountStatus",
                "active",
                "qualificationStatus",
                "not_submitted");
    }

    /**
     * 创建活动草稿详情占位数据。
     *
     * <p>前置条件：调用方需要 ActivityDraftDetail 结构。
     *
     * <p>后置条件：返回包含草稿必填字段的 Map。
     *
     * <p>不变量：不创建或更新真实草稿。
     */
    private Map<String, Object> activityDraftDetail() {
        return obj(
                "activityId",
                "activity-placeholder",
                "tags",
                List.of(),
                "images",
                List.of(),
                "reviewStatus",
                "draft",
                "updatedAt",
                NOW,
                "createdAt",
                NOW);
    }

    /**
     * 创建活动详情占位数据。
     *
     * <p>前置条件：调用方需要 ActivityDetail 结构。
     *
     * <p>后置条件：返回活动摘要字段与详情必填字段。
     *
     * <p>不变量：不查询真实活动、审核或报名记录。
     */
    private Map<String, Object> activityDetail() {
        Map<String, Object> value = activitySummary();
        value.put("introduction", "默认活动简介");
        value.put("safetyNotice", "默认安全须知");
        value.put("registrationDeadline", NOW);
        value.put("organizerId", "user-placeholder");
        value.put("organizerName", "MayoiStar");
        value.put("images", List.of());
        value.put("waitingCount", 0);
        value.put("manualReviewRequired", false);
        value.put("reviewRecords", List.of());
        return value;
    }

    /**
     * 创建活动摘要占位数据。
     *
     * <p>前置条件：调用方需要 ActivitySummary 结构。
     *
     * <p>后置条件：返回活动摘要必填字段。
     *
     * <p>不变量：不计算真实活动状态。
     */
    private Map<String, Object> activitySummary() {
        return obj(
                "activityId",
                "activity-placeholder",
                "title",
                "默认活动",
                "tags",
                List.of(),
                "startAt",
                NOW,
                "endAt",
                NOW,
                "location",
                location(),
                "reviewStatus",
                "approved",
                "runtimeStatus",
                "notStarted",
                "registeredCount",
                0,
                "capacity",
                20);
    }

    /**
     * 创建签到记录占位数据。
     *
     * <p>前置条件：调用方需要 CheckInRecord 结构。
     *
     * <p>后置条件：返回签到记录必填字段。
     *
     * <p>不变量：不记录真实签到。
     */
    private Map<String, Object> checkInRecord() {
        return obj(
                "registrationId",
                "registration-placeholder",
                "userId",
                "user-placeholder",
                "nickname",
                "MayoiStar",
                "registrationStatus",
                "checkedIn");
    }

    /**
     * 创建关注关系占位数据。
     *
     * <p>前置条件：调用方需要 FollowRelation 结构。
     *
     * <p>后置条件：返回关注关系必填字段。
     *
     * <p>不变量：不修改真实关注关系。
     */
    private Map<String, Object> followRelation() {
        return obj("targetUserId", "user-placeholder", "following", true, "mutual", false, "friendshipCreated", false);
    }

    /**
     * 创建好友申请占位数据。
     *
     * <p>前置条件：调用方需要 FriendRequest 结构。
     *
     * <p>后置条件：返回好友申请必填字段。
     *
     * <p>不变量：不创建真实好友申请。
     */
    private Map<String, Object> friendRequest() {
        return obj(
                "requestId",
                "request-placeholder",
                "requesterId",
                "user-placeholder",
                "targetUserId",
                "target-placeholder",
                "source",
                "profile",
                "status",
                "pending",
                "createdAt",
                NOW);
    }

    /**
     * 创建好友条目占位数据。
     *
     * <p>前置条件：调用方需要 FriendItem 结构。
     *
     * <p>后置条件：返回好友条目必填字段。
     *
     * <p>不变量：不读取真实好友列表。
     */
    private Map<String, Object> friendItem() {
        return obj(
                "userId",
                "user-placeholder",
                "nickname",
                "MayoiStar",
                "groupTags",
                List.of(),
                "source",
                "manualRequest");
    }

    /**
     * 创建用户举报占位数据。
     *
     * <p>前置条件：调用方需要 UserReport 结构。
     *
     * <p>后置条件：返回用户举报必填字段。
     *
     * <p>不变量：不创建或处理真实举报。
     */
    private Map<String, Object> userReport() {
        return obj(
                "reportId",
                "report-placeholder",
                "reporterUserId",
                "user-placeholder",
                "targetUserId",
                "target-placeholder",
                "reason",
                "契约占位举报理由",
                "status",
                "pending",
                "createdAt",
                NOW);
    }

    /**
     * 创建后台用户摘要占位数据。
     *
     * <p>前置条件：调用方需要 AdminUserSummary 结构。
     *
     * <p>后置条件：返回后台用户摘要必填字段。
     *
     * <p>不变量：不读取真实后台用户数据。
     */
    private Map<String, Object> adminUserSummary() {
        return obj(
                "userId",
                "user-placeholder",
                "email",
                "user@example.com",
                "kind",
                "personal",
                "status",
                "active",
                "activityCount",
                0,
                "teamCount",
                0,
                "createdAt",
                NOW);
    }

    /**
     * 创建小队资料占位数据。
     *
     * <p>前置条件：调用方需要 TeamProfile 结构。
     *
     * <p>后置条件：返回小队资料必填字段。
     *
     * <p>不变量：不读取真实小队状态。
     */
    private Map<String, Object> teamProfile() {
        return obj(
                "teamId",
                "team-placeholder",
                "name",
                "默认小队",
                "tags",
                List.of(),
                "joinMode",
                "publicJoin",
                "capacity",
                20,
                "memberCount",
                1,
                "status",
                "active",
                "leaderId",
                "user-placeholder",
                "chatId",
                "conversation-placeholder");
    }

    /**
     * 创建入队申请占位数据。
     *
     * <p>前置条件：调用方需要 TeamJoinRequest 结构。
     *
     * <p>后置条件：返回入队申请必填字段。
     *
     * <p>不变量：不创建或审批真实入队申请。
     */
    private Map<String, Object> teamJoinRequest() {
        return obj(
                "requestId",
                "request-placeholder",
                "teamId",
                "team-placeholder",
                "userId",
                "user-placeholder",
                "status",
                "pending",
                "createdAt",
                NOW);
    }

    /**
     * 创建小队成员占位数据。
     *
     * <p>前置条件：调用方需要 TeamMember 结构。
     *
     * <p>后置条件：返回成员必填字段。
     *
     * <p>不变量：不读取真实成员关系。
     */
    private Map<String, Object> teamMember() {
        return obj(
                "userId", "user-placeholder", "nickname", "MayoiStar", "role", "member", "points", 0, "joinedAt", NOW);
    }

    /**
     * 创建聊天消息占位数据。
     *
     * <p>前置条件：调用方需要 ChatMessage 结构。
     *
     * <p>后置条件：返回消息必填字段。
     *
     * <p>不变量：不创建、读取或发送真实消息。
     */
    private Map<String, Object> chatMessage() {
        return obj(
                "messageId",
                "message-placeholder",
                "conversationId",
                "conversation-placeholder",
                "senderId",
                "user-placeholder",
                "kind",
                "text",
                "text",
                "默认消息",
                "readStatus",
                "unread",
                "recalled",
                false,
                "sentAt",
                NOW);
    }

    /**
     * 创建群公告占位数据。
     *
     * <p>前置条件：调用方需要 TeamAnnouncement 结构。
     *
     * <p>后置条件：返回群公告必填字段。
     *
     * <p>不变量：不发布或读取真实公告。
     */
    private Map<String, Object> teamAnnouncement() {
        return obj(
                "announcementId",
                "announcement-placeholder",
                "teamId",
                "team-placeholder",
                "content",
                "默认公告",
                "publisherId",
                "user-placeholder",
                "publishedAt",
                NOW,
                "readByCurrentUser",
                false);
    }

    /**
     * 创建群投票占位数据。
     *
     * <p>前置条件：调用方需要 TeamPoll 结构。
     *
     * <p>后置条件：返回群投票必填字段与一个选项。
     *
     * <p>不变量：不创建或统计真实投票。
     */
    private Map<String, Object> teamPoll() {
        return obj(
                "pollId",
                "poll-placeholder",
                "teamId",
                "team-placeholder",
                "title",
                "默认投票",
                "options",
                List.of(obj("optionId", "option-placeholder", "content", "默认选项", "voteCount", 0)),
                "createdAt",
                NOW);
    }

    /**
     * 创建聊天实时事件占位数据。
     *
     * <p>前置条件：调用方需要 ChatRealtimeEvent 结构。
     *
     * <p>后置条件：返回实时消息事件必填字段。
     *
     * <p>不变量：不建立真实 WebSocket 连接。
     */
    private Map<String, Object> chatRealtimeEvent() {
        return obj(
                "kind",
                "messageCreated",
                "conversationId",
                "conversation-placeholder",
                "message",
                chatMessage(),
                "conversationUnreadCount",
                0,
                "occurredAt",
                NOW);
    }

    /**
     * 创建媒体文件占位数据。
     *
     * <p>前置条件：mediaId 与 usage 对应契约枚举值。
     *
     * <p>后置条件：返回媒体文件必填字段。
     *
     * <p>不变量：不保存真实文件。
     */
    private Map<String, Object> media(String mediaId, String usage) {
        return obj(
                "mediaId",
                mediaId,
                "fileName",
                "placeholder.png",
                "contentType",
                "image/png",
                "sizeBytes",
                0L,
                "usage",
                usage,
                "uploadedAt",
                NOW);
    }

    /**
     * 创建地点信息占位数据。
     *
     * <p>前置条件：调用方需要 LocationInfo 结构。
     *
     * <p>后置条件：返回地理坐标、城市和地址字段。
     *
     * <p>不变量：不调用地图或定位服务。
     */
    private Map<String, Object> location() {
        return obj(
                "point",
                obj("longitude", 116.397, "latitude", 39.907),
                "city",
                "北京",
                "address",
                "默认地址",
                "placeName",
                "默认地点");
    }

    /**
     * 创建保持插入顺序的 Map。
     *
     * <p>前置条件：entries 按 key、value 成对传入，key 为 String。
     *
     * <p>后置条件：返回包含全部键值对的 LinkedHashMap。
     *
     * <p>不变量：该方法不校验业务语义。
     */
    private Map<String, Object> obj(Object... entries) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            value.put((String) entries[index], entries[index + 1]);
        }
        return value;
    }
}

package io.github.layjason.mayoistar.api.common;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import java.util.List;
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

    /* ========== 通用 ========== */

    /**
     * 创建空分页结果响应。
     *
     * <p>前置条件：调用方需要分页占位响应。
     *
     * <p>后置条件：返回第 1 页、每页 20 条、无数据的分页结果。
     *
     * <p>不变量：该方法不访问数据库，不修改外部状态。
     *
     * @param <T> 分页条目类型
     * @return 空分页响应
     */
    public <T> ResponseEntity<ApiResponse<PageResult<T>>> emptyPage() {
        return ResponseEntity.ok(ApiResponse.success(PageResult.empty()));
    }

    /**
     * 创建空数据响应。
     *
     * <p>前置条件：调用方需要 EmptyData 占位响应。
     *
     * <p>后置条件：返回 data 为 JSON 空对象的成功响应。
     *
     * <p>不变量：该方法不访问数据库，不修改外部状态。
     *
     * @return 空数据响应
     */
    public ResponseEntity<ApiResponse<EmptyData>> emptyData() {
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    /**
     * 创建媒体文件占位响应。
     *
     * <p>前置条件：usage 对应契约枚举值。
     *
     * <p>后置条件：返回媒体文件必填字段的占位对象。
     *
     * <p>不变量：不保存真实文件。
     *
     * @param usage 媒体用途
     * @return 媒体文件响应
     */
    public ResponseEntity<ApiResponse<CommonDtos.MediaFile>> mediaFile(String usage) {
        CommonDtos.MediaFile file = new CommonDtos.MediaFile();
        file.setMediaId("media-placeholder");
        file.setFileName("placeholder.png");
        file.setContentType("image/png");
        file.setSizeBytes(0L);
        file.setUsage(usage);
        file.setUploadedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(file));
    }

    /* ========== 活动 ========== */

    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> activityDraftDetail() {
        ActivityDtos.ActivityDraftDetail dto = new ActivityDtos.ActivityDraftDetail();
        dto.setActivityId("activity-placeholder");
        dto.setTags(List.of());
        dto.setImages(List.of());
        dto.setReviewStatus("draft");
        dto.setUpdatedAt(NOW);
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> activityDetail() {
        ActivityDtos.ActivityDetail dto = new ActivityDtos.ActivityDetail();
        dto.setActivityId("activity-placeholder");
        dto.setTitle("默认活动");
        dto.setTags(List.of());
        dto.setStartAt(NOW);
        dto.setEndAt(NOW);
        dto.setLocation(location());
        dto.setReviewStatus("approved");
        dto.setRuntimeStatus("notStarted");
        dto.setRegisteredCount(0);
        dto.setCapacity(20);
        dto.setIntroduction("默认活动简介");
        dto.setSafetyNotice("默认安全须知");
        dto.setRegistrationDeadline(NOW);
        dto.setOrganizerId("user-placeholder");
        dto.setOrganizerName("MayoiStar");
        dto.setImages(List.of());
        dto.setWaitingCount(0);
        dto.setManualReviewRequired(false);
        dto.setReviewRecords(List.of());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<ActivityDtos.ActivitySummary>> activitySummary() {
        ActivityDtos.ActivitySummary dto = new ActivityDtos.ActivitySummary();
        fillActivitySummary(dto);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<ActivityDtos.RegistrationResult>> registrationResult() {
        ActivityDtos.RegistrationResult dto = new ActivityDtos.RegistrationResult();
        dto.setRegistrationId("registration-placeholder");
        dto.setActivityId("activity-placeholder");
        dto.setStatus("registered");
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<ActivityDtos.ActivityParticipationState>> participationState() {
        ActivityDtos.ActivityParticipationState dto = new ActivityDtos.ActivityParticipationState();
        dto.setCanRegister(true);
        dto.setCanCancelRegistration(false);
        dto.setCanConfirmWaitingSeat(false);
        dto.setCanCheckIn(false);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<ActivityDtos.CheckInQrCode>> checkInQrCode() {
        ActivityDtos.CheckInQrCode dto = new ActivityDtos.CheckInQrCode();
        dto.setActivityId("activity-placeholder");
        dto.setQrCodeToken("qr-placeholder");
        dto.setExpiresAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<ActivityDtos.CheckInRecord>> checkInRecord() {
        ActivityDtos.CheckInRecord dto = new ActivityDtos.CheckInRecord();
        dto.setRegistrationId("registration-placeholder");
        dto.setUserId("user-placeholder");
        dto.setNickname("MayoiStar");
        dto.setRegistrationStatus("checkedIn");
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<List<ActivityDtos.CheckInRecord>>> checkInRecords() {
        return ResponseEntity.ok(ApiResponse.success(List.of()));
    }

    public ResponseEntity<ApiResponse<ActivityDtos.ActivitySummaryPost>> activitySummaryPost() {
        ActivityDtos.ActivitySummaryPost dto = new ActivityDtos.ActivitySummaryPost();
        dto.setSummaryId("summary-placeholder");
        dto.setActivityId("activity-placeholder");
        dto.setTitle("默认总结");
        dto.setContent("默认总结内容");
        dto.setImages(List.of());
        dto.setImageTags(List.of());
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<ActivityDtos.ActivityReview>> activityReview() {
        ActivityDtos.ActivityReview dto = new ActivityDtos.ActivityReview();
        dto.setReviewId("review-placeholder");
        dto.setActivityId("activity-placeholder");
        dto.setUserId("user-placeholder");
        dto.setRating(5);
        dto.setTags(List.of());
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<List<ActivityDtos.ActivityMapPoint>>> activityMapPoints() {
        return ResponseEntity.ok(ApiResponse.success(List.of()));
    }

    /* ========== 身份认证 ========== */

    public ResponseEntity<ApiResponse<IdentityDtos.LoginResult>> loginResult() {
        IdentityDtos.TokenPair tokens = new IdentityDtos.TokenPair();
        tokens.setAccessToken("access-token-placeholder");
        tokens.setRefreshToken("refresh-token-placeholder");
        tokens.setExpiresAt(NOW);

        IdentityDtos.LoginResult dto = new IdentityDtos.LoginResult();
        dto.setUserId("user-placeholder");
        dto.setKind("personal");
        dto.setAccountStatus("active");
        dto.setTokens(tokens);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<IdentityDtos.TokenPair>> tokenPair() {
        IdentityDtos.TokenPair dto = new IdentityDtos.TokenPair();
        dto.setAccessToken("access-token-placeholder");
        dto.setRefreshToken("refresh-token-placeholder");
        dto.setExpiresAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<AdminDtos.AdminLoginResponse>> adminLoginResult() {
        IdentityDtos.TokenPair tokens = new IdentityDtos.TokenPair();
        tokens.setAccessToken("access-token-placeholder");
        tokens.setRefreshToken("refresh-token-placeholder");
        tokens.setExpiresAt(NOW);

        AdminDtos.AdminLoginResponse dto = new AdminDtos.AdminLoginResponse();
        dto.setUserId("admin-placeholder");
        dto.setTokens(tokens);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<IdentityDtos.PublicUserProfile>> publicUserProfile() {
        IdentityDtos.PublicUserProfile dto = new IdentityDtos.PublicUserProfile();
        dto.setUserId("user-placeholder");
        dto.setNickname("MayoiStar");
        dto.setInterestTags(List.of());
        dto.setReputationScore(100);
        dto.setKind("personal");
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<IdentityDtos.MerchantProfile>> merchantProfile() {
        IdentityDtos.MerchantProfile dto = new IdentityDtos.MerchantProfile();
        dto.setUserId("merchant-placeholder");
        dto.setMerchantName("默认商家");
        dto.setMerchantNickname("默认商家昵称");
        dto.setInterestedActivityFields(List.of());
        dto.setAccountStatus("active");
        dto.setQualificationStatus("not_submitted");
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<IdentityDtos.NicknameAvailability>> nicknameAvailability() {
        IdentityDtos.NicknameAvailability dto = new IdentityDtos.NicknameAvailability();
        dto.setNickname("MayoiStar");
        dto.setAvailable(true);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<List<IdentityDtos.InterestTagItem>>> interestTags() {
        return ResponseEntity.ok(ApiResponse.success(List.of()));
    }

    /* ========== 聊天 ========== */

    public ResponseEntity<ApiResponse<ChatDtos.ChatMessage>> chatMessage() {
        ChatDtos.ChatMessage dto = new ChatDtos.ChatMessage();
        dto.setMessageId("message-placeholder");
        dto.setConversationId("conversation-placeholder");
        dto.setSenderId("user-placeholder");
        dto.setKind("text");
        dto.setText("默认消息");
        dto.setReadStatus("unread");
        dto.setRecalled(false);
        dto.setSentAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<List<ChatDtos.ChatMessage>>> chatMessages() {
        return ResponseEntity.ok(ApiResponse.success(List.of()));
    }

    public ResponseEntity<ApiResponse<ChatDtos.ChatRealtimeEvent>> chatRealtimeEvent() {
        ChatDtos.ChatMessage message = new ChatDtos.ChatMessage();
        message.setMessageId("message-placeholder");
        message.setConversationId("conversation-placeholder");
        message.setSenderId("user-placeholder");
        message.setKind("text");
        message.setText("默认消息");
        message.setReadStatus("unread");
        message.setRecalled(false);
        message.setSentAt(NOW);

        ChatDtos.ChatRealtimeEvent dto = new ChatDtos.ChatRealtimeEvent();
        dto.setKind("messageCreated");
        dto.setConversationId("conversation-placeholder");
        dto.setMessage(message);
        dto.setConversationUnreadCount(0);
        dto.setOccurredAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<ChatDtos.TeamAnnouncement>> teamAnnouncement() {
        ChatDtos.TeamAnnouncement dto = new ChatDtos.TeamAnnouncement();
        dto.setAnnouncementId("announcement-placeholder");
        dto.setTeamId("team-placeholder");
        dto.setContent("默认公告");
        dto.setPublisherId("user-placeholder");
        dto.setPublishedAt(NOW);
        dto.setReadByCurrentUser(false);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<ChatDtos.TeamPoll>> teamPoll() {
        ChatDtos.TeamPollOption option = new ChatDtos.TeamPollOption();
        option.setOptionId("option-placeholder");
        option.setContent("默认选项");
        option.setVoteCount(0);

        ChatDtos.TeamPoll dto = new ChatDtos.TeamPoll();
        dto.setPollId("poll-placeholder");
        dto.setTeamId("team-placeholder");
        dto.setTitle("默认投票");
        dto.setOptions(List.of(option));
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /* ========== 社交 ========== */

    public ResponseEntity<ApiResponse<SocialDtos.FollowRelation>> followRelation() {
        SocialDtos.FollowRelation dto = new SocialDtos.FollowRelation();
        dto.setTargetUserId("user-placeholder");
        dto.setFollowing(true);
        dto.setMutual(false);
        dto.setFriendshipCreated(false);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<SocialDtos.FriendRequest>> friendRequest() {
        SocialDtos.FriendRequest dto = new SocialDtos.FriendRequest();
        dto.setRequestId("request-placeholder");
        dto.setRequesterId("user-placeholder");
        dto.setTargetUserId("target-placeholder");
        dto.setSource("profile");
        dto.setStatus("pending");
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<SocialDtos.FriendItem>> friendItem() {
        SocialDtos.FriendItem dto = new SocialDtos.FriendItem();
        dto.setUserId("user-placeholder");
        dto.setNickname("MayoiStar");
        dto.setGroupTags(List.of());
        dto.setSource("manualRequest");
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<SocialDtos.UserReport>> userReport() {
        SocialDtos.UserReport dto = new SocialDtos.UserReport();
        dto.setReportId("report-placeholder");
        dto.setReporterUserId("user-placeholder");
        dto.setTargetUserId("target-placeholder");
        dto.setReason("契约占位举报理由");
        dto.setStatus("pending");
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<AdminDtos.AdminUserSummary>> adminUserSummary() {
        AdminDtos.AdminUserSummary dto = new AdminDtos.AdminUserSummary();
        dto.setUserId("user-placeholder");
        dto.setEmail("user@example.com");
        dto.setKind("personal");
        dto.setStatus("active");
        dto.setActivityCount(0);
        dto.setTeamCount(0);
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<SocialDtos.TeamProfile>> teamProfile() {
        SocialDtos.TeamProfile dto = new SocialDtos.TeamProfile();
        dto.setTeamId("team-placeholder");
        dto.setName("默认小队");
        dto.setTags(List.of());
        dto.setJoinMode("publicJoin");
        dto.setCapacity(20);
        dto.setMemberCount(1);
        dto.setStatus("active");
        dto.setLeaderId("user-placeholder");
        dto.setChatId("conversation-placeholder");
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<SocialDtos.TeamJoinRequest>> teamJoinRequest() {
        SocialDtos.TeamJoinRequest dto = new SocialDtos.TeamJoinRequest();
        dto.setRequestId("request-placeholder");
        dto.setTeamId("team-placeholder");
        dto.setUserId("user-placeholder");
        dto.setStatus("pending");
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<SocialDtos.TeamMember>> teamMember() {
        SocialDtos.TeamMember dto = new SocialDtos.TeamMember();
        dto.setUserId("user-placeholder");
        dto.setNickname("MayoiStar");
        dto.setRole("member");
        dto.setPoints(0);
        dto.setJoinedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /* ========== AI ========== */

    public ResponseEntity<ApiResponse<AiDtos.ActivityPlanningResult>> activityPlanningResult() {
        AiDtos.ActivityPlanningResult dto = new AiDtos.ActivityPlanningResult();
        dto.setStatus("succeeded");
        dto.setTags(List.of());
        dto.setTitle("默认活动策划");
        dto.setIntroduction("契约占位活动简介");
        dto.setSafetyNotice("契约占位安全须知");
        dto.setSuggestedCapacity(20);
        dto.setSuggestedRegistrationDeadline(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    public ResponseEntity<ApiResponse<AiDtos.ImageClassificationResult>> imageClassificationResult() {
        AiDtos.ImageClassificationResult dto = new AiDtos.ImageClassificationResult();
        dto.setStatus("succeeded");
        dto.setItems(List.of());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /* ========== 内部辅助方法 ========== */

    /**
     * 填充活动摘要公共字段。
     *
     * <p>前置条件：dto 为 ActivitySummary 或其子类型实例。
     *
     * <p>后置条件：dto 的摘要字段均已设置占位值。
     *
     * <p>不变量：不读取或修改外部状态。
     */
    private void fillActivitySummary(ActivityDtos.ActivitySummary dto) {
        dto.setActivityId("activity-placeholder");
        dto.setTitle("默认活动");
        dto.setTags(List.of());
        dto.setStartAt(NOW);
        dto.setEndAt(NOW);
        dto.setLocation(location());
        dto.setReviewStatus("approved");
        dto.setRuntimeStatus("notStarted");
        dto.setRegisteredCount(0);
        dto.setCapacity(20);
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
    private CommonDtos.LocationInfo location() {
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(116.397);
        point.setLatitude(39.907);

        CommonDtos.LocationInfo loc = new CommonDtos.LocationInfo();
        loc.setPoint(point);
        loc.setCity("北京");
        loc.setAddress("默认地址");
        loc.setPlaceName("默认地点");
        return loc;
    }
}

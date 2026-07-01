package io.github.layjason.mayoistar.api.social;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.api.common.EmptyData;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.common.SecurityUtils;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.service.BlacklistService;
import io.github.layjason.mayoistar.service.FollowService;
import io.github.layjason.mayoistar.service.FriendRequestService;
import io.github.layjason.mayoistar.service.FriendshipService;
import io.github.layjason.mayoistar.service.ReportService;
import io.github.layjason.mayoistar.service.SocialProfileService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/social")
public class SocialController {

    private final DefaultApiResponseFactory responseFactory;
    private final BlacklistService blacklistService;
    private final FriendRequestService friendRequestService;
    private final FriendshipService friendshipService;
    private final ReportService reportService;
    private final FollowService followService;
    private final SocialProfileService socialProfileService;
    private final SecurityUtils securityUtils;

    /* ========== 黑名单 ========== */

    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.BlacklistItem>>> listBlacklist(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        var result = blacklistService.listBlacklist(securityUtils.getCurrentUserId(), page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/blacklist/{targetUserId}")
    public ResponseEntity<ApiResponse<EmptyData>> blockUser(@PathVariable String targetUserId) {
        blacklistService.blockUser(securityUtils.getCurrentUserId(), targetUserId);
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @DeleteMapping("/blacklist/{targetUserId}")
    public ResponseEntity<ApiResponse<EmptyData>> unblockUser(@PathVariable String targetUserId) {
        blacklistService.unblockUser(securityUtils.getCurrentUserId(), targetUserId);
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    /* ========== 好友申请 ========== */

    @PostMapping("/friend-requests")
    public ResponseEntity<ApiResponse<SocialDtos.FriendRequest>> createFriendRequest(
            @Valid @RequestBody SocialDtos.FriendRequestCreate request) {
        var result = friendRequestService.createFriendRequest(
                securityUtils.getCurrentUserId(), request.getTargetUserId(),
                request.getSource(), request.getMessage());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/friend-requests/received")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FriendRequest>>> listReceivedFriendRequests(
            @RequestParam(required = false) FriendRequestStatus status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        var result =
                friendRequestService.listReceivedRequests(securityUtils.getCurrentUserId(), status, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/friend-requests/sent")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FriendRequest>>> listSentFriendRequests(
            @RequestParam(required = false) FriendRequestStatus status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        var result = friendRequestService.listSentRequests(securityUtils.getCurrentUserId(), status, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/friend-requests/{requestId}/decision")
    public ResponseEntity<ApiResponse<SocialDtos.FriendRequest>> decideFriendRequest(
            @PathVariable String requestId, @Valid @RequestBody SocialDtos.FriendRequestDecision request) {
        var result = friendRequestService.decideFriendRequest(
                securityUtils.getCurrentUserId(), requestId, request.getAccepted());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /* ========== 好友管理 ========== */

    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FriendItem>>> listFriends(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        var result = friendshipService.listFriends(securityUtils.getCurrentUserId(), page, pageSize, keyword);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/friends/{userId}")
    public ResponseEntity<ApiResponse<SocialDtos.FriendItem>> updateFriendRemark(
            @PathVariable String userId, @Valid @RequestBody SocialDtos.FriendRemarkUpdate request) {
        var result = friendshipService.updateFriendRemark(
                securityUtils.getCurrentUserId(), userId, request.getRemark(), request.getGroupTags());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/friends/{userId}")
    public ResponseEntity<ApiResponse<EmptyData>> deleteFriend(@PathVariable String userId) {
        friendshipService.deleteFriend(securityUtils.getCurrentUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    /* ========== 举报 ========== */

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<SocialDtos.Report>> createReport(
            @Valid @RequestBody SocialDtos.ReportCreateRequest request) {
        var result = reportService.createReport(
                securityUtils.getCurrentUserId(), request.getTargetType(),
                request.getTargetId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.Report>>> listMyReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        var result = reportService.listMyReports(securityUtils.getCurrentUserId(), status, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /* ========== 小队（未实现，保持stub） ========== */

    @PostMapping("/teams")
    public ResponseEntity<ApiResponse<SocialDtos.TeamProfile>> createTeam(
            @Valid @RequestBody SocialDtos.TeamCreateRequest request) {
        return responseFactory.teamProfile();
    }

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.TeamProfile>>> searchTeams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/teams/{teamId}")
    public ResponseEntity<ApiResponse<SocialDtos.TeamProfile>> getTeam(@PathVariable String teamId) {
        return responseFactory.teamProfile();
    }

    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<ApiResponse<EmptyData>> dissolveTeam(@PathVariable String teamId) {
        return responseFactory.emptyData();
    }

    @PostMapping("/teams/{teamId}/activities")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> createTeamActivity(
            @PathVariable String teamId, @Valid @RequestBody ActivityDtos.ActivityUpsertRequest request) {
        return responseFactory.activityDetail();
    }

    @GetMapping("/teams/{teamId}/activities")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummary>>> listTeamActivities(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/teams/{teamId}/activities/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> getTeamActivity(
            @PathVariable String teamId, @PathVariable String activityId) {
        return responseFactory.activityDetail();
    }

    @PostMapping("/teams/{teamId}/join")
    public ResponseEntity<ApiResponse<SocialDtos.TeamJoinRequest>> joinTeam(
            @PathVariable String teamId, @Valid @RequestBody SocialDtos.JoinTeamRequest request) {
        return responseFactory.teamJoinRequest();
    }

    @GetMapping("/teams/{teamId}/join-requests")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.TeamJoinRequest>>> listTeamJoinRequests(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PostMapping("/teams/{teamId}/join-requests/{requestId}/decision")
    public ResponseEntity<ApiResponse<SocialDtos.TeamJoinRequest>> decideTeamJoinRequest(
            @PathVariable String teamId,
            @PathVariable String requestId,
            @Valid @RequestBody SocialDtos.TeamJoinRequestDecision request) {
        return responseFactory.teamJoinRequest();
    }

    @PostMapping("/teams/{teamId}/leave")
    public ResponseEntity<ApiResponse<EmptyData>> leaveTeam(@PathVariable String teamId) {
        return responseFactory.emptyData();
    }

    @GetMapping("/teams/{teamId}/members")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.TeamMember>>> listTeamMembers(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PatchMapping("/teams/{teamId}/members/{memberId}/role")
    public ResponseEntity<ApiResponse<SocialDtos.TeamMember>> updateTeamMemberRole(
            @PathVariable String teamId,
            @PathVariable String memberId,
            @Valid @RequestBody SocialDtos.TeamMemberRoleUpdate request) {
        return responseFactory.teamMember();
    }

    @GetMapping("/teams/{teamId}/points")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.TeamPointRankItem>>> getTeamPointRanks(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    /* ========== 关注与资料 ========== */

    @GetMapping("/followers")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FollowItem>>> listFollowers(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        var result = followService.listFollowers(securityUtils.getCurrentUserId(), page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/follows")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FollowItem>>> listFollows(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        var result = followService.listFollows(securityUtils.getCurrentUserId(), page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/follows/{targetUserId}")
    public ResponseEntity<ApiResponse<SocialDtos.FollowRelation>> followUser(@PathVariable String targetUserId) {
        var result = followService.followUser(securityUtils.getCurrentUserId(), targetUserId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/follows/{targetUserId}")
    public ResponseEntity<ApiResponse<SocialDtos.FollowRelation>> unfollowUser(@PathVariable String targetUserId) {
        var result = followService.unfollowUser(securityUtils.getCurrentUserId(), targetUserId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/profiles/{userId}")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.identity.IdentityDtos.PublicUserProfile>>
            getUserProfile(@PathVariable String userId) {
        var result = socialProfileService.getUserProfile(securityUtils.getCurrentUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}

package io.github.layjason.mayoistar.api.social;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.api.common.PageResult;
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

    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.BlacklistItem>>> listBlacklist(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PostMapping("/blacklist/{targetUserId}")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> blockUser(
            @PathVariable String targetUserId) {
        return responseFactory.emptyData();
    }

    @DeleteMapping("/blacklist/{targetUserId}")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> unblockUser(
            @PathVariable String targetUserId) {
        return responseFactory.emptyData();
    }

    @GetMapping("/followers")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FollowItem>>> listFollowers(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/follows")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FollowItem>>> listFollows(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PostMapping("/follows/{targetUserId}")
    public ResponseEntity<ApiResponse<SocialDtos.FollowRelation>> followUser(@PathVariable String targetUserId) {
        return responseFactory.followRelation();
    }

    @DeleteMapping("/follows/{targetUserId}")
    public ResponseEntity<ApiResponse<SocialDtos.FollowRelation>> unfollowUser(@PathVariable String targetUserId) {
        return responseFactory.followRelation();
    }

    @PostMapping("/friend-requests")
    public ResponseEntity<ApiResponse<SocialDtos.FriendRequest>> createFriendRequest(
            @Valid @RequestBody SocialDtos.FriendRequestCreate request) {
        return responseFactory.friendRequest();
    }

    @GetMapping("/friend-requests/received")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FriendRequest>>> listReceivedFriendRequests(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/friend-requests/sent")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FriendRequest>>> listSentFriendRequests(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PostMapping("/friend-requests/{requestId}/decision")
    public ResponseEntity<ApiResponse<SocialDtos.FriendRequest>> decideFriendRequest(
            @PathVariable String requestId, @Valid @RequestBody SocialDtos.FriendRequestDecision request) {
        return responseFactory.friendRequest();
    }

    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.FriendItem>>> listFriends(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PatchMapping("/friends/{userId}")
    public ResponseEntity<ApiResponse<SocialDtos.FriendItem>> updateFriendRemark(
            @PathVariable String userId, @Valid @RequestBody SocialDtos.FriendRemarkUpdate request) {
        return responseFactory.friendItem();
    }

    @DeleteMapping("/friends/{userId}")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> deleteFriend(
            @PathVariable String userId) {
        return responseFactory.emptyData();
    }

    @GetMapping("/profiles/{userId}")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.identity.IdentityDtos.PublicUserProfile>>
            getUserProfile(@PathVariable String userId) {
        return responseFactory.publicUserProfile();
    }

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
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> dissolveTeam(
            @PathVariable String teamId) {
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
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> leaveTeam(
            @PathVariable String teamId) {
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

    @PostMapping("/user-reports")
    public ResponseEntity<ApiResponse<SocialDtos.UserReport>> createUserReport(
            @Valid @RequestBody SocialDtos.UserReportCreateRequest request) {
        return responseFactory.userReport();
    }

    @GetMapping("/user-reports")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.UserReport>>> listMyUserReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }
}

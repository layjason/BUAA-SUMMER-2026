package io.github.layjason.mayoistar.api.social;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/social")
public class SocialController {

    private final DefaultApiResponseFactory responseFactory;

    public SocialController(DefaultApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<Object>> listBlacklist(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/blacklist");
    }

    @PostMapping("/blacklist/{targetUserId}")
    public ResponseEntity<ApiResponse<Object>> blockUser(@PathVariable String targetUserId) {
        return responseFactory.success("POST", "/social/blacklist/" + targetUserId);
    }

    @DeleteMapping("/blacklist/{targetUserId}")
    public ResponseEntity<ApiResponse<Object>> unblockUser(@PathVariable String targetUserId) {
        return responseFactory.success("DELETE", "/social/blacklist/" + targetUserId);
    }

    @GetMapping("/followers")
    public ResponseEntity<ApiResponse<Object>> listFollowers(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/followers");
    }

    @GetMapping("/follows")
    public ResponseEntity<ApiResponse<Object>> listFollows(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/follows");
    }

    @PostMapping("/follows/{targetUserId}")
    public ResponseEntity<ApiResponse<Object>> followUser(@PathVariable String targetUserId) {
        return responseFactory.success("POST", "/social/follows/" + targetUserId);
    }

    @DeleteMapping("/follows/{targetUserId}")
    public ResponseEntity<ApiResponse<Object>> unfollowUser(@PathVariable String targetUserId) {
        return responseFactory.success("DELETE", "/social/follows/" + targetUserId);
    }

    @PostMapping("/friend-requests")
    public ResponseEntity<ApiResponse<Object>> createFriendRequest(
            @Valid @RequestBody SocialDtos.FriendRequestCreate request) {
        return responseFactory.success("POST", "/social/friend-requests");
    }

    @GetMapping("/friend-requests/received")
    public ResponseEntity<ApiResponse<Object>> listReceivedFriendRequests(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/friend-requests/received");
    }

    @GetMapping("/friend-requests/sent")
    public ResponseEntity<ApiResponse<Object>> listSentFriendRequests(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/friend-requests/sent");
    }

    @PostMapping("/friend-requests/{requestId}/decision")
    public ResponseEntity<ApiResponse<Object>> decideFriendRequest(
            @PathVariable String requestId, @Valid @RequestBody SocialDtos.FriendRequestDecision request) {
        return responseFactory.success("POST", "/social/friend-requests/" + requestId + "/decision");
    }

    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<Object>> listFriends(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/friends");
    }

    @PatchMapping("/friends/{userId}")
    public ResponseEntity<ApiResponse<Object>> updateFriendRemark(
            @PathVariable String userId, @Valid @RequestBody SocialDtos.FriendRemarkUpdate request) {
        return responseFactory.success("PATCH", "/social/friends/" + userId);
    }

    @DeleteMapping("/friends/{userId}")
    public ResponseEntity<ApiResponse<Object>> deleteFriend(@PathVariable String userId) {
        return responseFactory.success("DELETE", "/social/friends/" + userId);
    }

    @GetMapping("/profiles/{userId}")
    public ResponseEntity<ApiResponse<Object>> getUserProfile(@PathVariable String userId) {
        return responseFactory.success("GET", "/social/profiles/" + userId);
    }

    @PostMapping("/teams")
    public ResponseEntity<ApiResponse<Object>> createTeam(@Valid @RequestBody SocialDtos.TeamCreateRequest request) {
        return responseFactory.success("POST", "/social/teams");
    }

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<Object>> searchTeams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/teams");
    }

    @GetMapping("/teams/{teamId}")
    public ResponseEntity<ApiResponse<Object>> getTeam(@PathVariable String teamId) {
        return responseFactory.success("GET", "/social/teams/" + teamId);
    }

    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<ApiResponse<Object>> dissolveTeam(@PathVariable String teamId) {
        return responseFactory.success("DELETE", "/social/teams/" + teamId);
    }

    @PostMapping("/teams/{teamId}/activities")
    public ResponseEntity<ApiResponse<Object>> createTeamActivity(
            @PathVariable String teamId, @Valid @RequestBody ActivityDtos.ActivityUpsertRequest request) {
        return responseFactory.success("POST", "/social/teams/" + teamId + "/activities");
    }

    @GetMapping("/teams/{teamId}/activities")
    public ResponseEntity<ApiResponse<Object>> listTeamActivities(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/teams/" + teamId + "/activities");
    }

    @GetMapping("/teams/{teamId}/activities/{activityId}")
    public ResponseEntity<ApiResponse<Object>> getTeamActivity(
            @PathVariable String teamId, @PathVariable String activityId) {
        return responseFactory.success("GET", "/social/teams/" + teamId + "/activities/" + activityId);
    }

    @PostMapping("/teams/{teamId}/join")
    public ResponseEntity<ApiResponse<Object>> joinTeam(
            @PathVariable String teamId, @Valid @RequestBody SocialDtos.JoinTeamRequest request) {
        return responseFactory.success("POST", "/social/teams/" + teamId + "/join");
    }

    @GetMapping("/teams/{teamId}/join-requests")
    public ResponseEntity<ApiResponse<Object>> listTeamJoinRequests(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/teams/" + teamId + "/join-requests");
    }

    @PostMapping("/teams/{teamId}/join-requests/{requestId}/decision")
    public ResponseEntity<ApiResponse<Object>> decideTeamJoinRequest(
            @PathVariable String teamId,
            @PathVariable String requestId,
            @Valid @RequestBody SocialDtos.TeamJoinRequestDecision request) {
        return responseFactory.success("POST", "/social/teams/" + teamId + "/join-requests/" + requestId + "/decision");
    }

    @PostMapping("/teams/{teamId}/leave")
    public ResponseEntity<ApiResponse<Object>> leaveTeam(@PathVariable String teamId) {
        return responseFactory.success("POST", "/social/teams/" + teamId + "/leave");
    }

    @GetMapping("/teams/{teamId}/members")
    public ResponseEntity<ApiResponse<Object>> listTeamMembers(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/teams/" + teamId + "/members");
    }

    @PatchMapping("/teams/{teamId}/members/{memberId}/role")
    public ResponseEntity<ApiResponse<Object>> updateTeamMemberRole(
            @PathVariable String teamId,
            @PathVariable String memberId,
            @Valid @RequestBody SocialDtos.TeamMemberRoleUpdate request) {
        return responseFactory.success("PATCH", "/social/teams/" + teamId + "/members/" + memberId + "/role");
    }

    @GetMapping("/teams/{teamId}/points")
    public ResponseEntity<ApiResponse<Object>> getTeamPointRanks(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/teams/" + teamId + "/points");
    }

    @PostMapping("/user-reports")
    public ResponseEntity<ApiResponse<Object>> createUserReport(
            @Valid @RequestBody SocialDtos.UserReportCreateRequest request) {
        return responseFactory.success("POST", "/social/user-reports");
    }

    @GetMapping("/user-reports")
    public ResponseEntity<ApiResponse<Object>> listMyUserReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/social/user-reports");
    }
}

package io.github.layjason.mayoistar.api.admin;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final DefaultApiResponseFactory responseFactory;

    @GetMapping("/activities")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummary>>> listActivities(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/activities/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> getActivity(@PathVariable String activityId) {
        return responseFactory.activityDetail();
    }

    @PostMapping("/activities/{activityId}/restore")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> restoreActivity(@PathVariable String activityId) {
        return responseFactory.activityDetail();
    }

    @PostMapping("/activities/{activityId}/review")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> reviewActivity(
            @PathVariable String activityId, @Valid @RequestBody AdminDtos.ReviewDecisionRequest request) {
        return responseFactory.activityDetail();
    }

    @PostMapping("/activities/{activityId}/take-down")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> takeDownActivity(
            @PathVariable String activityId, @Valid @RequestBody AdminDtos.ActivityModerationRequest request) {
        return responseFactory.activityDetail();
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<IdentityDtos.LoginResult>> login(
            @Valid @RequestBody AdminDtos.AdminLoginRequest request) {
        return responseFactory.loginResult();
    }

    @PostMapping("/auth/password")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> changePassword(
            @Valid @RequestBody AdminDtos.AdminChangePasswordRequest request) {
        return responseFactory.emptyData();
    }

    @GetMapping("/merchants/{merchantId}")
    public ResponseEntity<ApiResponse<IdentityDtos.MerchantProfile>> getMerchant(@PathVariable String merchantId) {
        return responseFactory.merchantProfile();
    }

    @PostMapping("/merchants/{merchantId}/review")
    public ResponseEntity<ApiResponse<IdentityDtos.MerchantProfile>> reviewMerchant(
            @PathVariable String merchantId, @Valid @RequestBody AdminDtos.MerchantReviewRequest request) {
        return responseFactory.merchantProfile();
    }

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.TeamProfile>>> listTeams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PostMapping("/teams/{teamId}/disable")
    public ResponseEntity<ApiResponse<SocialDtos.TeamProfile>> disableTeam(
            @PathVariable String teamId, @Valid @RequestBody AdminDtos.TeamModerationRequest request) {
        return responseFactory.teamProfile();
    }

    @PostMapping("/teams/{teamId}/restore")
    public ResponseEntity<ApiResponse<SocialDtos.TeamProfile>> restoreTeam(@PathVariable String teamId) {
        return responseFactory.teamProfile();
    }

    @GetMapping("/user-reports")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.UserReport>>> listUserReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PostMapping("/user-reports/{reportId}/decision")
    public ResponseEntity<ApiResponse<SocialDtos.UserReport>> decideUserReport(
            @PathVariable String reportId, @Valid @RequestBody AdminDtos.UserReportDecisionRequest request) {
        return responseFactory.userReport();
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResult<AdminDtos.AdminUserSummary>>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<ApiResponse<AdminDtos.AdminUserSummary>> banUser(
            @PathVariable String userId, @Valid @RequestBody AdminDtos.BanUserRequest request) {
        return responseFactory.adminUserSummary();
    }

    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<ApiResponse<AdminDtos.AdminUserSummary>> unbanUser(@PathVariable String userId) {
        return responseFactory.adminUserSummary();
    }
}

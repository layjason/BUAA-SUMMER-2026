package io.github.layjason.mayoistar.api.admin;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final DefaultApiResponseFactory responseFactory;

    public AdminController(DefaultApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @GetMapping("/activities")
    public ResponseEntity<ApiResponse<Object>> listActivities(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/admin/activities");
    }

    @GetMapping("/activities/{activityId}")
    public ResponseEntity<ApiResponse<Object>> getActivity(@PathVariable String activityId) {
        return responseFactory.success("GET", "/admin/activities/" + activityId);
    }

    @PostMapping("/activities/{activityId}/restore")
    public ResponseEntity<ApiResponse<Object>> restoreActivity(@PathVariable String activityId) {
        return responseFactory.success("POST", "/admin/activities/" + activityId + "/restore");
    }

    @PostMapping("/activities/{activityId}/review")
    public ResponseEntity<ApiResponse<Object>> reviewActivity(
            @PathVariable String activityId, @Valid @RequestBody AdminDtos.ReviewDecisionRequest request) {
        return responseFactory.success("POST", "/admin/activities/" + activityId + "/review");
    }

    @PostMapping("/activities/{activityId}/take-down")
    public ResponseEntity<ApiResponse<Object>> takeDownActivity(
            @PathVariable String activityId, @Valid @RequestBody AdminDtos.ActivityModerationRequest request) {
        return responseFactory.success("POST", "/admin/activities/" + activityId + "/take-down");
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<Object>> login(@Valid @RequestBody AdminDtos.AdminLoginRequest request) {
        return responseFactory.success("POST", "/admin/auth/login");
    }

    @PostMapping("/auth/password")
    public ResponseEntity<ApiResponse<Object>> changePassword(
            @Valid @RequestBody AdminDtos.AdminChangePasswordRequest request) {
        return responseFactory.success("POST", "/admin/auth/password");
    }

    @GetMapping("/merchants/{merchantId}")
    public ResponseEntity<ApiResponse<Object>> getMerchant(@PathVariable String merchantId) {
        return responseFactory.success("GET", "/admin/merchants/" + merchantId);
    }

    @PostMapping("/merchants/{merchantId}/review")
    public ResponseEntity<ApiResponse<Object>> reviewMerchant(
            @PathVariable String merchantId, @Valid @RequestBody AdminDtos.MerchantReviewRequest request) {
        return responseFactory.success("POST", "/admin/merchants/" + merchantId + "/review");
    }

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<Object>> listTeams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/admin/teams");
    }

    @PostMapping("/teams/{teamId}/disable")
    public ResponseEntity<ApiResponse<Object>> disableTeam(
            @PathVariable String teamId, @Valid @RequestBody AdminDtos.TeamModerationRequest request) {
        return responseFactory.success("POST", "/admin/teams/" + teamId + "/disable");
    }

    @PostMapping("/teams/{teamId}/restore")
    public ResponseEntity<ApiResponse<Object>> restoreTeam(@PathVariable String teamId) {
        return responseFactory.success("POST", "/admin/teams/" + teamId + "/restore");
    }

    @GetMapping("/user-reports")
    public ResponseEntity<ApiResponse<Object>> listUserReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/admin/user-reports");
    }

    @PostMapping("/user-reports/{reportId}/decision")
    public ResponseEntity<ApiResponse<Object>> decideUserReport(
            @PathVariable String reportId, @Valid @RequestBody AdminDtos.UserReportDecisionRequest request) {
        return responseFactory.success("POST", "/admin/user-reports/" + reportId + "/decision");
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Object>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/admin/users");
    }

    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<ApiResponse<Object>> banUser(
            @PathVariable String userId, @Valid @RequestBody AdminDtos.BanUserRequest request) {
        return responseFactory.success("POST", "/admin/users/" + userId + "/ban");
    }

    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<ApiResponse<Object>> unbanUser(@PathVariable String userId) {
        return responseFactory.success("POST", "/admin/users/" + userId + "/unban");
    }
}

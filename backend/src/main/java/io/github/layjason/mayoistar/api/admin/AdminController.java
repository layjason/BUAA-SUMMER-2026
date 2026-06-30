package io.github.layjason.mayoistar.api.admin;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.api.common.EmptyData;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.service.AdminAuthService;
import io.github.layjason.mayoistar.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final AdminAuthService adminAuthService;
    private final AdminService adminService;

    @GetMapping("/activities")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummary>>> listActivities(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ActivityReviewStatus reviewStatus,
            @RequestParam(required = false) ActivityRuntimeStatus runtimeStatus,
            @RequestParam(required = false) String organizerId,
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
    public ResponseEntity<ApiResponse<AdminDtos.AdminLoginResponse>> login(
            @Valid @RequestBody AdminDtos.AdminLoginRequest request) {
        AdminDtos.AdminLoginResponse result = adminAuthService.login(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/auth/password")
    public ResponseEntity<ApiResponse<EmptyData>> changePassword(
            @Valid @RequestBody AdminDtos.AdminChangePasswordRequest request) {
        String adminId = getCurrentAdminId();
        adminAuthService.changePassword(adminId, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
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
            @RequestParam(required = false) TeamStatus status,
            @RequestParam(required = false) String creatorId,
            @RequestParam(required = false) String leaderId,
            @RequestParam(required = false) String memberUserId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/teams/{teamId}")
    public ResponseEntity<ApiResponse<AdminDtos.AdminTeamDetail>> getTeam(@PathVariable String teamId) {
        return responseFactory.adminTeamDetail();
    }

    @GetMapping("/teams/{teamId}/activities")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummary>>> listTeamActivities(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/teams/{teamId}/members")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.TeamMember>>> listTeamMembers(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/teams/{teamId}/reports")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.Report>>> listTeamReports(
            @PathVariable String teamId,
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

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.Report>>> listReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) String reporterUserId,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PostMapping("/reports/{reportId}/decision")
    public ResponseEntity<ApiResponse<SocialDtos.Report>> decideReport(
            @PathVariable String reportId, @Valid @RequestBody AdminDtos.ReportDecisionRequest request) {
        return responseFactory.report();
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResult<AdminDtos.AdminUserSummary>>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserKind kind,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) QualificationStatus qualificationStatus,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminDtos.AdminUserDetail>> getUser(@PathVariable String userId) {
        return responseFactory.adminUserDetail();
    }

    @GetMapping("/users/{userId}/activities")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummary>>> listUserActivities(
            @PathVariable String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/users/{userId}/teams")
    public ResponseEntity<ApiResponse<PageResult<SocialDtos.TeamProfile>>> listUserTeams(
            @PathVariable String userId,
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

    /**
     * 从 SecurityContext 获取当前认证管理员的 ID。
     *
     * <p>前置条件：请求已通过 JWT 认证过滤器或测试 @WithMockUser。SecurityContext 中存在有效的 Authentication。
     *
     * <p>后置条件：返回当前管理员 ID。支持 String principal（JWT 场景）和 UserDetails principal（测试场景）。
     *
     * @return 当前管理员 ID
     */
    private String getCurrentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BusinessException(401, "Authentication is required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String adminId) {
            return adminId;
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        throw new BusinessException(401, "Authentication is required");
    }
}

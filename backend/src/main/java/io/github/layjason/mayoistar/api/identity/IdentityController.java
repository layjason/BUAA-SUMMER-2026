package io.github.layjason.mayoistar.api.identity;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.api.common.EmptyData;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.service.AuthService;
import io.github.layjason.mayoistar.service.UserProfileService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/identity")
public class IdentityController {

    private final AuthService authService;
    private final UserProfileService userProfileService;
    private final DefaultApiResponseFactory responseFactory;

    @PostMapping("/auth/register/personal")
    public ResponseEntity<ApiResponse<EmptyData>> registerPersonal(
            @Valid @RequestBody IdentityDtos.PersonalRegisterRequest request) {
        authService.registerPersonal(request);
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<IdentityDtos.LoginResult>> login(
            @Valid @RequestBody IdentityDtos.EmailPasswordRequest request) {
        IdentityDtos.LoginResult result = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/auth/activate")
    public ResponseEntity<ApiResponse<EmptyData>> activateAccount(
            @Valid @RequestBody IdentityDtos.AccountActivationRequest request) {
        authService.activateAccount(request.getToken());
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @PostMapping("/auth/activation-email")
    public ResponseEntity<ApiResponse<EmptyData>> resendActivationEmail(
            @Valid @RequestBody IdentityDtos.ResendActivationEmailRequest request) {
        authService.resendActivationEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiResponse<IdentityDtos.TokenPair>> refreshToken(
            @Valid @RequestBody IdentityDtos.RefreshTokenRequest request) {
        IdentityDtos.TokenPair result = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/auth/password-reset-email")
    public ResponseEntity<ApiResponse<EmptyData>> sendPasswordResetEmail(
            @Valid @RequestBody IdentityDtos.PasswordResetEmailRequest request) {
        authService.sendPasswordResetEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @PostMapping("/auth/password-reset")
    public ResponseEntity<ApiResponse<EmptyData>> resetPassword(
            @Valid @RequestBody IdentityDtos.ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<EmptyData>> logout() {
        String userId = getCurrentUserId();
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<EmptyData>> changePassword(
            @Valid @RequestBody IdentityDtos.ChangePasswordRequest request) {
        String userId = getCurrentUserId();
        authService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<IdentityDtos.PublicUserProfile>> getMyProfile() {
        String userId = getCurrentUserId();
        IdentityDtos.PublicUserProfile profile = userProfileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<ApiResponse<IdentityDtos.PublicUserProfile>> updatePersonalProfile(
            @Valid @RequestBody IdentityDtos.UpdatePersonalProfileRequest request) {
        String userId = getCurrentUserId();
        IdentityDtos.PublicUserProfile profile = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @GetMapping("/interest-tags")
    public ResponseEntity<ApiResponse<List<IdentityDtos.InterestTagItem>>> getInterestTags() {
        List<IdentityDtos.InterestTagItem> tags = userProfileService.getInterestTags();
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    @GetMapping("/nicknames/availability")
    public ResponseEntity<ApiResponse<IdentityDtos.NicknameAvailability>> checkNickname(@RequestParam String nickname) {
        IdentityDtos.NicknameAvailability result = userProfileService.checkNickname(nickname);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/media/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CommonDtos.MediaFile>> uploadAvatar(
            @RequestPart(value = "file") MultipartFile file) {
        String userId = getCurrentUserId();
        CommonDtos.MediaFile result = userProfileService.uploadAvatar(userId, file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ---- 以下为商家相关占位端点，将在第二阶段实现 ----

    @PostMapping("/auth/register/merchant")
    public ResponseEntity<ApiResponse<EmptyData>> registerMerchant(
            @Valid @RequestBody IdentityDtos.MerchantRegisterRequest request) {
        return responseFactory.emptyData();
    }

    @GetMapping("/me/merchant-profile")
    public ResponseEntity<ApiResponse<IdentityDtos.MerchantProfile>> getMyMerchantProfile() {
        return responseFactory.merchantProfile();
    }

    @PatchMapping("/me/merchant-profile")
    public ResponseEntity<ApiResponse<IdentityDtos.MerchantProfile>> updateMerchantProfile(
            @Valid @RequestBody IdentityDtos.UpdateMerchantProfileRequest request) {
        return responseFactory.merchantProfile();
    }

    @PostMapping("/me/merchant-qualification")
    public ResponseEntity<ApiResponse<EmptyData>> submitMerchantQualification(
            @Valid @RequestBody IdentityDtos.QualificationSubmitRequest request) {
        return responseFactory.emptyData();
    }

    @PostMapping(value = "/media/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CommonDtos.MediaFile>> uploadMerchantLicense(
            @RequestPart(value = "file") MultipartFile file) {
        return responseFactory.mediaFile(MediaUsage.merchantLicense);
    }

    /**
     * 从 SecurityContext 获取当前认证用户的 ID。
     *
     * <p>前置条件：请求已通过 JWT 过滤器认证或测试场景注入。SecurityContext 中存在有效的 Authentication。
     *
     * <p>后置条件：返回当前用户 ID。支持 String principal（JWT 场景）和 UserDetails principal（测试 @WithMockUser 场景）。
     *
     * @return 当前用户 ID
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BusinessException(401, "Authentication is required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String userId) {
            return userId;
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        throw new BusinessException(401, "Authentication is required");
    }
}

package io.github.layjason.mayoistar.api.identity;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/identity")
public class IdentityController {

    private final DefaultApiResponseFactory responseFactory;

    public IdentityController(DefaultApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @PostMapping("/auth/activate")
    public ResponseEntity<ApiResponse<Object>> activateAccount(
            @Valid @RequestBody IdentityDtos.AccountActivationRequest request) {
        return responseFactory.success("POST", "/identity/auth/activate");
    }

    @PostMapping("/auth/activation-email")
    public ResponseEntity<ApiResponse<Object>> resendActivationEmail(
            @Valid @RequestBody IdentityDtos.ResendActivationEmailRequest request) {
        return responseFactory.success("POST", "/identity/auth/activation-email");
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<Object>> login(@Valid @RequestBody IdentityDtos.EmailPasswordRequest request) {
        return responseFactory.success("POST", "/identity/auth/login");
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Object>> logout() {
        return responseFactory.success("POST", "/identity/auth/logout");
    }

    @PostMapping("/auth/password-reset")
    public ResponseEntity<ApiResponse<Object>> resetPassword(
            @Valid @RequestBody IdentityDtos.ResetPasswordRequest request) {
        return responseFactory.success("POST", "/identity/auth/password-reset");
    }

    @PostMapping("/auth/password-reset-email")
    public ResponseEntity<ApiResponse<Object>> sendPasswordResetEmail(
            @Valid @RequestBody IdentityDtos.PasswordResetEmailRequest request) {
        return responseFactory.success("POST", "/identity/auth/password-reset-email");
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiResponse<Object>> refreshToken(
            @Valid @RequestBody IdentityDtos.RefreshTokenRequest request) {
        return responseFactory.success("POST", "/identity/auth/refresh");
    }

    @PostMapping("/auth/register/merchant")
    public ResponseEntity<ApiResponse<Object>> registerMerchant(
            @Valid @RequestBody IdentityDtos.MerchantRegisterRequest request) {
        return responseFactory.success("POST", "/identity/auth/register/merchant");
    }

    @PostMapping("/auth/register/personal")
    public ResponseEntity<ApiResponse<Object>> registerPersonal(
            @Valid @RequestBody IdentityDtos.PersonalRegisterRequest request) {
        return responseFactory.success("POST", "/identity/auth/register/personal");
    }

    @GetMapping("/interest-tags")
    public ResponseEntity<ApiResponse<Object>> getInterestTags() {
        return responseFactory.success("GET", "/identity/interest-tags");
    }

    @GetMapping("/me/merchant-profile")
    public ResponseEntity<ApiResponse<Object>> getMyMerchantProfile() {
        return responseFactory.success("GET", "/identity/me/merchant-profile");
    }

    @PatchMapping("/me/merchant-profile")
    public ResponseEntity<ApiResponse<Object>> updateMerchantProfile(
            @Valid @RequestBody IdentityDtos.UpdateMerchantProfileRequest request) {
        return responseFactory.success("PATCH", "/identity/me/merchant-profile");
    }

    @PostMapping(value = "/me/merchant-qualification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> submitMerchantQualification(
            @RequestPart(value = "licenseMediaId", required = false) String licenseMediaId) {
        return responseFactory.success("POST", "/identity/me/merchant-qualification");
    }

    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<Object>> changePassword(
            @Valid @RequestBody IdentityDtos.ChangePasswordRequest request) {
        return responseFactory.success("POST", "/identity/me/password");
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<Object>> getMyProfile() {
        return responseFactory.success("GET", "/identity/me/profile");
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<ApiResponse<Object>> updatePersonalProfile(
            @Valid @RequestBody IdentityDtos.UpdatePersonalProfileRequest request) {
        return responseFactory.success("PATCH", "/identity/me/profile");
    }

    @PostMapping(value = "/media/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> uploadAvatar(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return responseFactory.success("POST", "/identity/media/avatar");
    }

    @PostMapping(value = "/media/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> uploadMerchantLicense(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return responseFactory.success("POST", "/identity/media/license");
    }

    @GetMapping("/nicknames/availability")
    public ResponseEntity<ApiResponse<Object>> checkNickname(@RequestParam String nickname) {
        return responseFactory.success("GET", "/identity/nicknames/availability");
    }
}

package io.github.layjason.mayoistar.api.identity;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/identity")
public class IdentityController {

    private final DefaultApiResponseFactory responseFactory;

    @PostMapping("/auth/activate")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> activateAccount(
            @Valid @RequestBody IdentityDtos.AccountActivationRequest request) {
        return responseFactory.emptyData();
    }

    @PostMapping("/auth/activation-email")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> resendActivationEmail(
            @Valid @RequestBody IdentityDtos.ResendActivationEmailRequest request) {
        return responseFactory.emptyData();
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<IdentityDtos.LoginResult>> login(
            @Valid @RequestBody IdentityDtos.EmailPasswordRequest request) {
        return responseFactory.loginResult();
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> logout() {
        return responseFactory.emptyData();
    }

    @PostMapping("/auth/password-reset")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> resetPassword(
            @Valid @RequestBody IdentityDtos.ResetPasswordRequest request) {
        return responseFactory.emptyData();
    }

    @PostMapping("/auth/password-reset-email")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> sendPasswordResetEmail(
            @Valid @RequestBody IdentityDtos.PasswordResetEmailRequest request) {
        return responseFactory.emptyData();
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiResponse<IdentityDtos.TokenPair>> refreshToken(
            @Valid @RequestBody IdentityDtos.RefreshTokenRequest request) {
        return responseFactory.tokenPair();
    }

    @PostMapping("/auth/register/merchant")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> registerMerchant(
            @Valid @RequestBody IdentityDtos.MerchantRegisterRequest request) {
        return responseFactory.emptyData();
    }

    @PostMapping("/auth/register/personal")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> registerPersonal(
            @Valid @RequestBody IdentityDtos.PersonalRegisterRequest request) {
        return responseFactory.emptyData();
    }

    @GetMapping("/interest-tags")
    public ResponseEntity<ApiResponse<List<IdentityDtos.InterestTagItem>>> getInterestTags() {
        return responseFactory.interestTags();
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
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> submitMerchantQualification(
            @Valid @RequestBody IdentityDtos.QualificationSubmitRequest request) {
        return responseFactory.emptyData();
    }

    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> changePassword(
            @Valid @RequestBody IdentityDtos.ChangePasswordRequest request) {
        return responseFactory.emptyData();
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<IdentityDtos.PublicUserProfile>> getMyProfile() {
        return responseFactory.publicUserProfile();
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<ApiResponse<IdentityDtos.PublicUserProfile>> updatePersonalProfile(
            @Valid @RequestBody IdentityDtos.UpdatePersonalProfileRequest request) {
        return responseFactory.publicUserProfile();
    }

    @PostMapping(value = "/media/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile>> uploadAvatar(
            @RequestPart(value = "file") MultipartFile file) {
        return responseFactory.mediaFile(MediaUsage.avatar);
    }

    @PostMapping(value = "/media/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile>>
            uploadMerchantLicense(@RequestPart(value = "file") MultipartFile file) {
        return responseFactory.mediaFile(MediaUsage.merchantLicense);
    }

    @GetMapping("/nicknames/availability")
    public ResponseEntity<ApiResponse<IdentityDtos.NicknameAvailability>> checkNickname(@RequestParam String nickname) {
        return responseFactory.nicknameAvailability();
    }
}

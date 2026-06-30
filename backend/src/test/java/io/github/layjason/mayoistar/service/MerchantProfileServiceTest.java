package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import io.github.layjason.mayoistar.entity.identity.Qualification;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.MerchantProfileRepository;
import io.github.layjason.mayoistar.repository.QualificationRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantProfileRepository merchantProfileRepository;

    @Mock
    private QualificationRepository qualificationRepository;

    @Mock
    private MediaFileRepository mediaFileRepository;

    private MerchantProfileService merchantProfileService;

    private final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        merchantProfileService = new MerchantProfileService(
                userRepository, merchantProfileRepository, qualificationRepository, mediaFileRepository);
    }

    @Nested
    @DisplayName("获取商家资料")
    class GetMerchantProfile {

        @Test
        @DisplayName("成功获取商家资料")
        void shouldGetMerchantProfile() {
            User user = buildMerchantUser();
            MerchantProfile profile = buildMerchantProfile();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(qualificationRepository.findByUserId(userId)).thenReturn(Optional.empty());

            IdentityDtos.MerchantProfile result = merchantProfileService.getMerchantProfile(userId);

            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getNickname()).isEqualTo("merchant");
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.active);
            assertThat(result.getQualificationStatus()).isEqualTo(QualificationStatus.not_submitted);
        }

        @Test
        @DisplayName("非商家用户抛出 10008")
        void shouldThrowOnNonMerchant() {
            User user = buildMerchantUser();
            user.setKind(UserKind.personal);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> merchantProfileService.getMerchantProfile(userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10008);
        }
    }

    @Nested
    @DisplayName("更新商家资料")
    class UpdateMerchantProfile {

        @Test
        @DisplayName("成功更新商家名称")
        void shouldUpdateMerchantName() {
            User user = buildMerchantUser();
            MerchantProfile profile = buildMerchantProfile();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(qualificationRepository.findByUserId(userId)).thenReturn(Optional.empty());

            IdentityDtos.UpdateMerchantProfileRequest request = new IdentityDtos.UpdateMerchantProfileRequest();
            request.setMerchantName("新商家名称");

            IdentityDtos.MerchantProfile result = merchantProfileService.updateMerchantProfile(userId, request);

            assertThat(result.getMerchantName()).isEqualTo("新商家名称");
        }

        @Test
        @DisplayName("昵称被占用时抛出 10002")
        void shouldThrowOnNicknameUnavailable() {
            User user = buildMerchantUser();
            MerchantProfile profile = buildMerchantProfile();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(userRepository.existsByNickname("taken")).thenReturn(true);

            IdentityDtos.UpdateMerchantProfileRequest request = new IdentityDtos.UpdateMerchantProfileRequest();
            request.setNickname("taken");

            assertThatThrownBy(() -> merchantProfileService.updateMerchantProfile(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10002);
        }
    }

    @Nested
    @DisplayName("提交资质")
    class SubmitQualification {

        @Test
        @DisplayName("成功提交资质")
        void shouldSubmitQualification() {
            User user = buildMerchantUser();
            String licenseId = UUID.randomUUID().toString();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(mediaFileRepository.findById(licenseId))
                    .thenReturn(Optional.of(io.github.layjason.mayoistar.entity.common.MediaFile.builder()
                            .mediaId(licenseId)
                            .fileName("license.jpg")
                            .contentType("image/jpeg")
                            .sizeBytes(100L)
                            .usage(io.github.layjason.mayoistar.entity.common.MediaUsage.merchantLicense)
                            .storagePath("/path")
                            .uploadedBy(userId)
                            .uploadedAt(Instant.now())
                            .build()));
            when(qualificationRepository.existsByUserIdAndStatus(userId, QualificationStatus.pending))
                    .thenReturn(false);
            when(qualificationRepository.existsByUserIdAndStatus(userId, QualificationStatus.approved))
                    .thenReturn(false);
            when(qualificationRepository.findByUserId(userId)).thenReturn(Optional.empty());

            IdentityDtos.QualificationSubmitRequest request = new IdentityDtos.QualificationSubmitRequest();
            request.setLicenseMediaIds(List.of(licenseId));

            merchantProfileService.submitQualification(userId, request);

            ArgumentCaptor<Qualification> captor = ArgumentCaptor.forClass(Qualification.class);
            verify(qualificationRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(QualificationStatus.pending);
            assertThat(captor.getValue().getLicenseMediaIds()).containsExactly(licenseId);
        }

        @Test
        @DisplayName("资质已提交时抛出 10009")
        void shouldThrowOnAlreadySubmitted() {
            User user = buildMerchantUser();
            String licenseId = UUID.randomUUID().toString();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(mediaFileRepository.findById(licenseId))
                    .thenReturn(Optional.of(io.github.layjason.mayoistar.entity.common.MediaFile.builder()
                            .mediaId(licenseId)
                            .fileName("license.jpg")
                            .contentType("image/jpeg")
                            .sizeBytes(100L)
                            .usage(io.github.layjason.mayoistar.entity.common.MediaUsage.merchantLicense)
                            .storagePath("/path")
                            .uploadedBy(userId)
                            .uploadedAt(Instant.now())
                            .build()));
            when(qualificationRepository.existsByUserIdAndStatus(userId, QualificationStatus.pending))
                    .thenReturn(true);

            IdentityDtos.QualificationSubmitRequest request = new IdentityDtos.QualificationSubmitRequest();
            request.setLicenseMediaIds(List.of(licenseId));

            assertThatThrownBy(() -> merchantProfileService.submitQualification(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10009);
        }
    }

    private User buildMerchantUser() {
        return User.builder()
                .userId(userId)
                .email("merchant@example.com")
                .nickname("merchant")
                .passwordHash("hash")
                .kind(UserKind.merchant)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private MerchantProfile buildMerchantProfile() {
        return MerchantProfile.builder()
                .userId(userId)
                .merchantName("测试商家")
                .updatedAt(Instant.now())
                .build();
    }
}

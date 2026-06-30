package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.entity.admin.Admin;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import io.github.layjason.mayoistar.entity.identity.Qualification;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.AdminRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantProfileRepository merchantProfileRepository;

    @Mock
    private QualificationRepository qualificationRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private MediaFileRepository mediaFileRepository;

    private AdminService adminService;

    private final String adminId = UUID.randomUUID().toString();
    private final String merchantId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository,
                merchantProfileRepository,
                qualificationRepository,
                adminRepository,
                mediaFileRepository);
    }

    @Nested
    @DisplayName("审核商家资质")
    class ReviewMerchantQualification {

        @Test
        @DisplayName("通过商家资质")
        void shouldApproveMerchant() {
            Admin admin = buildAdmin();
            User user = buildMerchantUser();
            MerchantProfile profile = buildMerchantProfile();
            Qualification qualification = buildPendingQualification();

            when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.findById(merchantId)).thenReturn(Optional.of(user));
            when(qualificationRepository.findByUserIdAndStatus(merchantId, QualificationStatus.pending))
                    .thenReturn(Optional.of(qualification));
            when(merchantProfileRepository.findByUserId(merchantId)).thenReturn(Optional.of(profile));

            AdminDtos.MerchantReviewRequest request = new AdminDtos.MerchantReviewRequest();
            request.setApproved(true);

            var result = adminService.reviewMerchantQualification(merchantId, adminId, request);

            assertThat(result.getQualificationStatus()).isEqualTo(QualificationStatus.approved);
            assertThat(qualification.getStatus()).isEqualTo(QualificationStatus.approved);
            assertThat(qualification.getReviewedAt()).isNotNull();
            assertThat(qualification.getReviewerId()).isEqualTo(adminId);
        }

        @Test
        @DisplayName("驳回商家资质（有原因）")
        void shouldRejectMerchant() {
            Admin admin = buildAdmin();
            User user = buildMerchantUser();
            MerchantProfile profile = buildMerchantProfile();
            Qualification qualification = buildPendingQualification();

            when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.findById(merchantId)).thenReturn(Optional.of(user));
            when(qualificationRepository.findByUserIdAndStatus(merchantId, QualificationStatus.pending))
                    .thenReturn(Optional.of(qualification));
            when(merchantProfileRepository.findByUserId(merchantId)).thenReturn(Optional.of(profile));

            AdminDtos.MerchantReviewRequest request = new AdminDtos.MerchantReviewRequest();
            request.setApproved(false);
            request.setReason("资质不清晰");

            var result = adminService.reviewMerchantQualification(merchantId, adminId, request);

            assertThat(result.getQualificationStatus()).isEqualTo(QualificationStatus.rejected);
            assertThat(qualification.getStatus()).isEqualTo(QualificationStatus.rejected);
            assertThat(qualification.getRejectReason()).isEqualTo("资质不清晰");
        }

        @Test
        @DisplayName("驳回时无原因抛出异常")
        void shouldThrowWhenRejectWithoutReason() {
            Admin admin = buildAdmin();
            User user = buildMerchantUser();
            Qualification qualification = buildPendingQualification();

            when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.findById(merchantId)).thenReturn(Optional.of(user));
            when(qualificationRepository.findByUserIdAndStatus(merchantId, QualificationStatus.pending))
                    .thenReturn(Optional.of(qualification));

            AdminDtos.MerchantReviewRequest request = new AdminDtos.MerchantReviewRequest();
            request.setApproved(false);

            assertThatThrownBy(() -> adminService.reviewMerchantQualification(merchantId, adminId, request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("资质不在 pending 状态抛出异常")
        void shouldThrowWhenNotPending() {
            Admin admin = buildAdmin();
            User user = buildMerchantUser();

            when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.findById(merchantId)).thenReturn(Optional.of(user));
            when(qualificationRepository.findByUserIdAndStatus(merchantId, QualificationStatus.pending))
                    .thenReturn(Optional.empty());

            AdminDtos.MerchantReviewRequest request = new AdminDtos.MerchantReviewRequest();
            request.setApproved(true);

            assertThatThrownBy(() -> adminService.reviewMerchantQualification(merchantId, adminId, request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    private Admin buildAdmin() {
        return Admin.builder()
                .adminId(adminId)
                .username("admin")
                .passwordHash("hash")
                .build();
    }

    private User buildMerchantUser() {
        return User.builder()
                .userId(merchantId)
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
                .userId(merchantId)
                .merchantName("测试商家")
                .updatedAt(Instant.now())
                .build();
    }

    private Qualification buildPendingQualification() {
        return Qualification.builder()
                .qualificationId(UUID.randomUUID().toString())
                .userId(merchantId)
                .status(QualificationStatus.pending)
                .licenseMediaIds(List.of(UUID.randomUUID().toString()))
                .submittedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }
}

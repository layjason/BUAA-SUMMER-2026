package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import io.github.layjason.mayoistar.entity.identity.Qualification;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.AdminRepository;
import io.github.layjason.mayoistar.repository.BanRecordRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.MerchantProfileRepository;
import io.github.layjason.mayoistar.repository.QualificationRepository;
import io.github.layjason.mayoistar.repository.ReportRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamModerationRecordRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.service.activities.ActivityRegistrationCountService;
import io.github.layjason.mayoistar.service.activities.AdminActivityService;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AdminServiceListUsersIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantProfileRepository merchantProfileRepository;

    @Autowired
    private QualificationRepository qualificationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private EntityManager entityManager;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        qualificationRepository.deleteAll();
        merchantProfileRepository.deleteAll();
        userRepository.deleteAll();
        adminService = new AdminService(
                userRepository,
                merchantProfileRepository,
                qualificationRepository,
                mock(AdminRepository.class),
                mock(MediaFileRepository.class),
                mock(BanRecordRepository.class),
                activityRepository,
                teamRepository,
                mock(TeamMemberRepository.class),
                mock(TeamModerationRecordRepository.class),
                mock(ReportRepository.class),
                mock(ReportService.class),
                mock(ActivityRegistrationCountService.class),
                mock(MediaAccessService.class),
                mock(AdminActivityService.class));
    }

    @Test
    @DisplayName("商家资质筛选在数据库分页前生效")
    void shouldApplyQualificationStatusBeforePagination() {
        for (int index = 0; index < 15; index++) {
            saveMerchant("approved-merchant-" + index, QualificationStatus.approved, "已通过商家" + index);
        }
        for (int index = 0; index < 5; index++) {
            saveMerchant("pending-merchant-" + index, QualificationStatus.pending, "待审商家" + index);
        }

        PageResult<AdminDtos.AdminUserSummary> result =
                adminService.listUsers(null, UserKind.merchant, null, QualificationStatus.approved, 1, 10);

        assertThat(result.getItems()).hasSize(10);
        assertThat(result.getItems())
                .extracting(AdminDtos.AdminUserSummary::getQualificationStatus)
                .containsOnly(QualificationStatus.approved);
        assertThat(result.getTotal()).isEqualTo(15L);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("用户关键词查询包含商家名称")
    void shouldSearchMerchantNameByKeyword() {
        saveMerchant("merchant-name-hit", QualificationStatus.approved, "星河露营社");
        saveMerchant("merchant-name-miss", QualificationStatus.approved, "普通桌游社");

        PageResult<AdminDtos.AdminUserSummary> result =
                adminService.listUsers("星河", UserKind.merchant, null, QualificationStatus.approved, 1, 10);

        assertThat(result.getItems())
                .extracting(AdminDtos.AdminUserSummary::getUserId)
                .containsExactly("merchant-name-hit");
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    private void saveMerchant(String userId, QualificationStatus status, String merchantName) {
        User user = userRepository.save(User.builder()
                .userId(userId)
                .email(userId + "@example.com")
                .nickname(userId)
                .passwordHash("password-hash")
                .kind(UserKind.merchant)
                .accountStatus(AccountStatus.active)
                .activatedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .createdAt(Instant.parse("2026-07-01T00:00:00Z").plusSeconds(userId.hashCode() & 0xFFFFL))
                .updatedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build());
        MerchantProfile merchantProfile = MerchantProfile.builder()
                .user(user)
                .merchantName(merchantName)
                .updatedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
        entityManager.persist(merchantProfile);
        qualificationRepository.save(Qualification.builder()
                .qualificationId(userId + "-qualification")
                .userId(userId)
                .user(user)
                .status(status)
                .createdAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build());
    }
}

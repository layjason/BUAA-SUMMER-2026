package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.admin.Admin;
import io.github.layjason.mayoistar.entity.admin.BanRecord;
import io.github.layjason.mayoistar.entity.admin.TeamModerationRecord;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import io.github.layjason.mayoistar.entity.identity.Qualification;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.Team;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
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

    @Mock
    private BanRecordRepository banRecordRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TeamModerationRecordRepository teamModerationRecordRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportService reportService;

    @Mock
    private ActivityRegistrationCountService activityRegistrationCountService;

    @Mock
    private MediaAccessService mediaAccessService;

    @Mock
    private AdminActivityService adminActivityService;

    private AdminService adminService;

    private final String adminId = UUID.randomUUID().toString();
    private final String userId = UUID.randomUUID().toString();
    private final String teamId = UUID.randomUUID().toString();
    private final String activityId = UUID.randomUUID().toString();
    private final String reportId = UUID.randomUUID().toString();
    private final UUID avatarMediaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository,
                merchantProfileRepository,
                qualificationRepository,
                adminRepository,
                mediaFileRepository,
                banRecordRepository,
                activityRepository,
                teamRepository,
                teamMemberRepository,
                teamModerationRecordRepository,
                reportRepository,
                reportService,
                activityRegistrationCountService,
                mediaAccessService,
                adminActivityService);
    }

    @Nested
    @DisplayName("管理员查看商家资料")
    class GetMerchantForAdmin {

        @Test
        @DisplayName("返回头像签名 URL")
        void shouldReturnSignedAvatarUrl() {
            User user = buildMerchantUser();
            MerchantProfile profile = buildMerchantProfile();
            profile.setAvatarMediaId(avatarMediaId);
            MediaFile avatar = buildAvatarMediaFile();
            CommonDtos.MediaFile signedAvatar = buildSignedAvatarDto();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(qualificationRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(mediaFileRepository.findById(avatarMediaId)).thenReturn(Optional.of(avatar));
            when(mediaAccessService.toSignedDto(avatar)).thenReturn(signedAvatar);

            var result = adminService.getMerchantForAdmin(userId);

            assertThat(result.getAvatar()).isSameAs(signedAvatar);
            assertThat(result.getAvatar().getSignedUrl()).isEqualTo("/common/media/" + avatarMediaId + "?sig=test");
            verify(mediaAccessService).toSignedDto(avatar);
        }
    }

    // ======================== 商家审核 ========================

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
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(qualificationRepository.findByUserIdAndStatus(userId, QualificationStatus.pending))
                    .thenReturn(Optional.of(qualification));
            when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

            AdminDtos.MerchantReviewRequest request = new AdminDtos.MerchantReviewRequest();
            request.setApproved(true);

            var result = adminService.reviewMerchantQualification(userId, adminId, request);

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
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(qualificationRepository.findByUserIdAndStatus(userId, QualificationStatus.pending))
                    .thenReturn(Optional.of(qualification));
            when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

            AdminDtos.MerchantReviewRequest request = new AdminDtos.MerchantReviewRequest();
            request.setApproved(false);
            request.setReason("资质不清晰");

            var result = adminService.reviewMerchantQualification(userId, adminId, request);

            assertThat(result.getQualificationStatus()).isEqualTo(QualificationStatus.rejected);
            assertThat(qualification.getRejectReason()).isEqualTo("资质不清晰");
        }

        @Test
        @DisplayName("驳回时无原因抛出 60006")
        void shouldThrowWhenRejectWithoutReason() {
            Admin admin = buildAdmin();
            User user = buildMerchantUser();
            Qualification qualification = buildPendingQualification();

            when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(qualificationRepository.findByUserIdAndStatus(userId, QualificationStatus.pending))
                    .thenReturn(Optional.of(qualification));

            AdminDtos.MerchantReviewRequest request = new AdminDtos.MerchantReviewRequest();
            request.setApproved(false);

            assertThatThrownBy(() -> adminService.reviewMerchantQualification(userId, adminId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60006);
        }

        @Test
        @DisplayName("资质不在 pending 状态抛出 60005")
        void shouldThrowWhenNotPending() {
            Admin admin = buildAdmin();
            User user = buildMerchantUser();

            when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(qualificationRepository.findByUserIdAndStatus(userId, QualificationStatus.pending))
                    .thenReturn(Optional.empty());

            AdminDtos.MerchantReviewRequest request = new AdminDtos.MerchantReviewRequest();
            request.setApproved(true);

            assertThatThrownBy(() -> adminService.reviewMerchantQualification(userId, adminId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60005);
        }
    }

    // ======================== 用户封禁/解封 ========================

    @Nested
    @DisplayName("封禁用户")
    class BanUser {

        @Test
        @DisplayName("成功封禁用户")
        void shouldBanUser() {
            User user = buildActiveUser();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            when(activityRepository.countByOrganizerId(userId)).thenReturn(3L);
            when(teamRepository.countByCreatorId(userId)).thenReturn(1L);
            when(banRecordRepository.save(any(BanRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            AdminDtos.BanUserRequest request = new AdminDtos.BanUserRequest();
            request.setReason("违规行为");
            request.setBannedUntil(Instant.now().plusSeconds(86400).toString());

            AdminDtos.AdminUserSummary result = adminService.banUser(userId, adminId, request);

            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.banned);
            assertThat(result.getStatus()).isEqualTo(AccountStatus.banned);
            verify(banRecordRepository).save(any(BanRecord.class));
        }

        @Test
        @DisplayName("已封禁用户再次封禁抛出 60003")
        void shouldThrowWhenAlreadyBanned() {
            User user = buildActiveUser();
            user.setAccountStatus(AccountStatus.banned);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            AdminDtos.BanUserRequest request = new AdminDtos.BanUserRequest();
            request.setReason("再次违规");
            request.setBannedUntil(Instant.now().plusSeconds(86400).toString());

            assertThatThrownBy(() -> adminService.banUser(userId, adminId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60003);
        }

        @Test
        @DisplayName("封禁原因为空抛出 60006")
        void shouldThrowWhenReasonIsBlank() {
            User user = buildActiveUser();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));

            AdminDtos.BanUserRequest request = new AdminDtos.BanUserRequest();
            request.setReason("");
            request.setBannedUntil(Instant.now().plusSeconds(86400).toString());

            assertThatThrownBy(() -> adminService.banUser(userId, adminId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60006);
        }
    }

    @Nested
    @DisplayName("解封用户")
    class UnbanUser {

        @Test
        @DisplayName("成功解封用户")
        void shouldUnbanUser() {
            User user = buildActiveUser();
            user.setAccountStatus(AccountStatus.banned);
            BanRecord banRecord = buildBanRecord();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            when(banRecordRepository.findActiveBanByUserId(userId)).thenReturn(Optional.of(banRecord));
            when(activityRepository.countByOrganizerId(userId)).thenReturn(0L);
            when(teamRepository.countByCreatorId(userId)).thenReturn(0L);

            AdminDtos.AdminUserSummary result = adminService.unbanUser(userId, adminId);

            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.active);
            assertThat(result.getStatus()).isEqualTo(AccountStatus.active);
            assertThat(banRecord.getUnbannedAt()).isNotNull();
        }

        @Test
        @DisplayName("未封禁用户解封抛出 60004")
        void shouldThrowWhenNotBanned() {
            User user = buildActiveUser();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> adminService.unbanUser(userId, adminId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60004);
        }
    }

    // ======================== 用户查询 ========================

    @Nested
    @DisplayName("获取用户详情")
    class GetUser {

        @Test
        @DisplayName("获取未封禁用户详情")
        void shouldGetUserDetailWithoutBan() {
            User user = buildActiveUser();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(activityRepository.countByOrganizerId(userId)).thenReturn(2L);
            when(teamRepository.countByCreatorId(userId)).thenReturn(1L);
            when(banRecordRepository.findActiveBanByUserId(userId)).thenReturn(Optional.empty());

            AdminDtos.AdminUserDetail result = adminService.getUser(userId);

            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getCurrentBanInfo()).isNull();
        }

        @Test
        @DisplayName("获取已封禁用户详情含封禁信息")
        void shouldGetUserDetailWithBan() {
            User user = buildActiveUser();
            user.setAccountStatus(AccountStatus.banned);
            BanRecord banRecord = buildBanRecord();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(activityRepository.countByOrganizerId(userId)).thenReturn(0L);
            when(teamRepository.countByCreatorId(userId)).thenReturn(0L);
            when(banRecordRepository.findActiveBanByUserId(userId)).thenReturn(Optional.of(banRecord));

            AdminDtos.AdminUserDetail result = adminService.getUser(userId);

            assertThat(result.getCurrentBanInfo()).isNotNull();
            assertThat(result.getCurrentBanInfo().getReason()).isEqualTo("违规行为");
        }

        @Test
        @DisplayName("用户不存在抛出 60002")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getUser(userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60002);
        }
    }

    // ======================== 举报管理 ========================

    @Nested
    @DisplayName("处理举报")
    class DecideReport {

        @Test
        @DisplayName("成功处理举报，委托给 ReportService")
        void shouldDecideReport() {
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));

            AdminDtos.ReportDecisionRequest request = new AdminDtos.ReportDecisionRequest();
            request.setStatus(ReportStatus.resolved);
            request.setHandlingNote("已处理");

            SocialDtos.Report expectedResult = new SocialDtos.Report();
            expectedResult.setReportId(reportId);
            expectedResult.setStatus(ReportStatus.resolved);
            expectedResult.setHandlingNote("已处理");
            when(reportService.decideReport(reportId, ReportStatus.resolved, "已处理"))
                    .thenReturn(expectedResult);

            var result = adminService.decideReport(reportId, adminId, request);

            assertThat(result.getStatus()).isEqualTo(ReportStatus.resolved);
            assertThat(result.getHandlingNote()).isEqualTo("已处理");
            verify(reportService).decideReport(reportId, ReportStatus.resolved, "已处理");
        }

        @Test
        @DisplayName("举报不存在时传播 ReportService 的异常")
        void shouldThrowWhenReportNotFound() {
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));

            AdminDtos.ReportDecisionRequest request = new AdminDtos.ReportDecisionRequest();
            request.setStatus(ReportStatus.resolved);
            request.setHandlingNote("已处理");
            when(reportService.decideReport(reportId, ReportStatus.resolved, "已处理"))
                    .thenThrow(new BusinessException(60007, "Report does not exist"));

            assertThatThrownBy(() -> adminService.decideReport(reportId, adminId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60007);
        }
    }

    // ======================== 活动管理 ========================

    @Nested
    @DisplayName("获取活动详情")
    class GetActivity {

        @Test
        @DisplayName("成功获取活动详情")
        void shouldGetActivity() {
            ActivityDtos.ActivityDetail expected = buildActivityDetail();
            when(adminActivityService.getActivityDetail(activityId)).thenReturn(expected);

            var result = adminService.getActivity(activityId);

            assertThat(result).isSameAs(expected);
            verify(adminActivityService).getActivityDetail(activityId);
        }

        @Test
        @DisplayName("活动不存在抛出 60008")
        void shouldThrowWhenActivityNotFound() {
            when(adminActivityService.getActivityDetail(activityId))
                    .thenThrow(new BusinessException(60008, "Activity does not exist"));

            assertThatThrownBy(() -> adminService.getActivity(activityId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60008);
        }
    }

    @Nested
    @DisplayName("下架活动")
    class TakeDownActivity {

        @Test
        @DisplayName("成功下架活动")
        void shouldTakeDownActivity() {
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            ActivityDtos.ActivityDetail expected = buildActivityDetail();

            AdminDtos.ActivityModerationRequest request = new AdminDtos.ActivityModerationRequest();
            request.setReason("违规内容");
            when(adminActivityService.takeDownActivity(activityId, adminId, "违规内容"))
                    .thenReturn(expected);

            var result = adminService.takeDownActivity(activityId, adminId, request);

            assertThat(result).isSameAs(expected);
            verify(adminActivityService).takeDownActivity(activityId, adminId, "违规内容");
        }

        @Test
        @DisplayName("已下架活动再次下架抛出 60009")
        void shouldThrowWhenAlreadyTakenDown() {
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));

            AdminDtos.ActivityModerationRequest request = new AdminDtos.ActivityModerationRequest();
            request.setReason("再次违规");
            when(adminActivityService.takeDownActivity(activityId, adminId, "再次违规"))
                    .thenThrow(new BusinessException(60009, "Activity moderation state does not allow this operation"));

            assertThatThrownBy(() -> adminService.takeDownActivity(activityId, adminId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60009);
        }
    }

    @Nested
    @DisplayName("恢复活动")
    class RestoreActivity {

        @Test
        @DisplayName("成功恢复活动")
        void shouldRestoreActivity() {
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            ActivityDtos.ActivityDetail expected = buildActivityDetail();
            when(adminActivityService.restoreActivity(activityId, adminId)).thenReturn(expected);

            var result = adminService.restoreActivity(activityId, adminId);

            assertThat(result).isSameAs(expected);
            verify(adminActivityService).restoreActivity(activityId, adminId);
        }

        @Test
        @DisplayName("未下架活动恢复抛出 60009")
        void shouldThrowWhenNotTakenDown() {
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            when(adminActivityService.restoreActivity(activityId, adminId))
                    .thenThrow(new BusinessException(60009, "Activity moderation state does not allow this operation"));

            assertThatThrownBy(() -> adminService.restoreActivity(activityId, adminId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60009);
        }
    }

    // ======================== 小队管理 ========================

    @Nested
    @DisplayName("获取小队详情")
    class GetTeam {

        @Test
        @DisplayName("成功获取小队详情含治理记录")
        void shouldGetTeamDetail() {
            Team team = buildTeam();
            when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
            when(teamMemberRepository.countByTeamId(teamId)).thenReturn(3L);
            when(teamModerationRecordRepository.findByTeamIdOrderByCreatedAtAsc(teamId))
                    .thenReturn(List.of());

            var result = adminService.getTeam(teamId);

            assertThat(result.getTeamId()).isEqualTo(teamId);
            assertThat(result.getName()).isEqualTo("测试小队");
        }

        @Test
        @DisplayName("小队不存在抛出 60010")
        void shouldThrowWhenTeamNotFound() {
            when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getTeam(teamId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60010);
        }
    }

    @Nested
    @DisplayName("停用小隊")
    class DisableTeam {

        @Test
        @DisplayName("成功停用小队")
        void shouldDisableTeam() {
            Team team = buildTeam();
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
            when(teamMemberRepository.countByTeamId(teamId)).thenReturn(2L);

            AdminDtos.TeamModerationRequest request = new AdminDtos.TeamModerationRequest();
            request.setReason("违规行为");

            var result = adminService.disableTeam(teamId, adminId, request);

            assertThat(team.getStatus()).isEqualTo(TeamStatus.disabled);
            verify(teamModerationRecordRepository).save(any(TeamModerationRecord.class));
        }

        @Test
        @DisplayName("非活跃小队停用抛出 60011")
        void shouldThrowWhenNotActive() {
            Team team = buildTeam();
            team.setStatus(TeamStatus.dissolved);
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

            AdminDtos.TeamModerationRequest request = new AdminDtos.TeamModerationRequest();
            request.setReason("违规");

            assertThatThrownBy(() -> adminService.disableTeam(teamId, adminId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60011);
        }
    }

    @Nested
    @DisplayName("恢复小队")
    class RestoreTeam {

        @Test
        @DisplayName("成功恢复小队")
        void shouldRestoreTeam() {
            Team team = buildTeam();
            team.setStatus(TeamStatus.disabled);
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
            when(teamMemberRepository.countByTeamId(teamId)).thenReturn(1L);

            var result = adminService.restoreTeam(teamId, adminId);

            assertThat(team.getStatus()).isEqualTo(TeamStatus.active);
            verify(teamModerationRecordRepository).save(any(TeamModerationRecord.class));
        }

        @Test
        @DisplayName("非停用小队恢复抛出 60011")
        void shouldThrowWhenNotDisabled() {
            Team team = buildTeam();
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

            assertThatThrownBy(() -> adminService.restoreTeam(teamId, adminId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60011);
        }
    }

    @Nested
    @DisplayName("活动审核")
    class ReviewActivity {

        @Test
        @DisplayName("审核通过活动")
        void shouldApproveActivity() {
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));
            ActivityDtos.ActivityDetail expected = buildActivityDetail();

            AdminDtos.ReviewDecisionRequest request = new AdminDtos.ReviewDecisionRequest();
            request.setResult(ReviewStatus.approved);
            when(adminActivityService.reviewActivity(activityId, adminId, ReviewStatus.approved, null))
                    .thenReturn(expected);

            var result = adminService.reviewActivity(activityId, adminId, request);

            assertThat(result).isSameAs(expected);
            verify(adminActivityService).reviewActivity(activityId, adminId, ReviewStatus.approved, null);
        }

        @Test
        @DisplayName("驳回活动但不给原因抛出 60006")
        void shouldThrowWhenRejectWithoutReason() {
            when(adminRepository.findById(adminId)).thenReturn(Optional.of(buildAdmin()));

            AdminDtos.ReviewDecisionRequest request = new AdminDtos.ReviewDecisionRequest();
            request.setResult(ReviewStatus.rejected);
            when(adminActivityService.reviewActivity(activityId, adminId, ReviewStatus.rejected, null))
                    .thenThrow(new BusinessException(60006, "Review reason is required"));

            assertThatThrownBy(() -> adminService.reviewActivity(activityId, adminId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60006);
        }
    }

    // ======================== 辅助构建方法 ========================

    private Admin buildAdmin() {
        return Admin.builder()
                .adminId(adminId)
                .username("admin")
                .passwordHash("hash")
                .build();
    }

    private User buildActiveUser() {
        return User.builder()
                .userId(userId)
                .email("user@example.com")
                .nickname("testuser")
                .passwordHash("hash")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
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

    private MediaFile buildAvatarMediaFile() {
        return MediaFile.builder()
                .mediaId(avatarMediaId)
                .fileName("avatar.png")
                .contentType("image/png")
                .sizeBytes(1024L)
                .usage(MediaUsage.avatar)
                .storagePath("avatars/" + avatarMediaId + ".png")
                .visibility(MediaVisibility.privateVisible)
                .uploadedBy(userId)
                .uploadedAt(Instant.now())
                .build();
    }

    private CommonDtos.MediaFile buildSignedAvatarDto() {
        CommonDtos.MediaFile dto = new CommonDtos.MediaFile();
        dto.setMediaId(avatarMediaId);
        dto.setFileName("avatar.png");
        dto.setContentType("image/png");
        dto.setSizeBytes(1024L);
        dto.setUsage(MediaUsage.avatar);
        dto.setVisibility(MediaVisibility.privateVisible);
        dto.setSignedUrl("/common/media/" + avatarMediaId + "?sig=test");
        dto.setUploadedAt(Instant.now().toString());
        return dto;
    }

    private Qualification buildPendingQualification() {
        return Qualification.builder()
                .qualificationId(UUID.randomUUID().toString())
                .userId(userId)
                .status(QualificationStatus.pending)
                .licenseMediaIds(List.of(UUID.randomUUID()))
                .submittedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    private BanRecord buildBanRecord() {
        return BanRecord.builder()
                .banId(UUID.randomUUID().toString())
                .userId(userId)
                .operatorId(adminId)
                .reason("违规行为")
                .bannedAt(Instant.now())
                .bannedUntil(Instant.now().plusSeconds(86400))
                .build();
    }

    private ActivityDtos.ActivityDetail buildActivityDetail() {
        ActivityDtos.ActivityDetail detail = new ActivityDtos.ActivityDetail();
        detail.setActivityId(activityId);
        detail.setTitle("测试活动");
        return detail;
    }

    private Team buildTeam() {
        return Team.builder()
                .teamId(teamId)
                .name("测试小队")
                .tags(List.of("运动"))
                .capacity(20)
                .status(TeamStatus.active)
                .creatorId(userId)
                .leaderId(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}

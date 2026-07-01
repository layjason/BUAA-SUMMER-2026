package io.github.layjason.mayoistar.api.admin;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.chat.Conversation;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import io.github.layjason.mayoistar.entity.identity.Qualification;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Report;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import io.github.layjason.mayoistar.entity.social.Team;
import io.github.layjason.mayoistar.entity.social.TeamJoinMode;
import io.github.layjason.mayoistar.entity.social.TeamMember;
import io.github.layjason.mayoistar.entity.social.TeamMemberRole;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.MerchantProfileRepository;
import io.github.layjason.mayoistar.repository.QualificationRepository;
import io.github.layjason.mayoistar.repository.ReportRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 后台管理合同测试数据初始化。
 *
 * <p>类职责：为 ApiContractControllerTests 创建占位测试数据，使得 admin 端点可返回成功响应而非业务错误。
 * 合同测试使用 "placeholder" 作为所有路径参数值。
 *
 * <p>不变量：仅在 test profile 时生效。
 */
@Configuration
@Profile("test")
public class AdminTestDataInitializer {

    /**
     * 合同测试中所有路径参数替换成的占位值。
     */
    public static final String PLACEHOLDER = "placeholder";

    @Bean
    public ApplicationRunner adminTestDataInitializerRunner(
            UserRepository userRepository,
            MerchantProfileRepository merchantProfileRepository,
            QualificationRepository qualificationRepository,
            ActivityRepository activityRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            ReportRepository reportRepository,
            ConversationRepository conversationRepository) {
        return args -> {
            createPlaceholderMerchantIfNeeded(userRepository, merchantProfileRepository, qualificationRepository);
            createPlaceholderActivityIfNeeded(activityRepository);
            createPlaceholderConversationIfNeeded(conversationRepository);
            createPlaceholderTeamIfNeeded(teamRepository, teamMemberRepository);
            createPlaceholderReportIfNeeded(reportRepository);
        };
    }

    public static void createPlaceholderConversationIfNeeded(ConversationRepository conversationRepository) {
        if (conversationRepository.existsById(PLACEHOLDER + "-chat")) {
            return;
        }
        Instant now = Instant.now();
        Conversation conversation = Conversation.builder()
                .conversationId(PLACEHOLDER + "-chat")
                .kind(ConversationKind.team)
                .title("Placeholder Chat")
                .createdAt(now)
                .updatedAt(now)
                .build();
        conversationRepository.save(conversation);
    }

    public static void createPlaceholderMerchantIfNeeded(
            UserRepository userRepository,
            MerchantProfileRepository merchantProfileRepository,
            QualificationRepository qualificationRepository) {
        if (userRepository.existsById(PLACEHOLDER)) {
            return;
        }
        Instant now = Instant.now();
        User user = User.builder()
                .userId(PLACEHOLDER)
                .email("placeholder@example.com")
                .nickname("placeholder")
                .passwordHash("$2a$12$dummyhash")
                .kind(UserKind.merchant)
                .accountStatus(AccountStatus.active)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userRepository.save(user);

        MerchantProfile profile = new MerchantProfile();
        profile.setUser(user);
        profile.setMerchantName("Placeholder Merchant");
        profile.setInterestedActivityFields(List.of());
        profile.setUpdatedAt(now);
        merchantProfileRepository.save(profile);

        Qualification qualification = Qualification.builder()
                .qualificationId(UUID.randomUUID().toString())
                .userId(PLACEHOLDER)
                .status(QualificationStatus.pending)
                .licenseMediaIds(List.of())
                .submittedAt(now)
                .createdAt(now)
                .build();
        qualificationRepository.save(qualification);
    }

    public static void createPlaceholderActivityIfNeeded(ActivityRepository activityRepository) {
        if (activityRepository.existsById(PLACEHOLDER)) {
            return;
        }
        Instant now = Instant.now();
        Activity activity = Activity.builder()
                .activityId(PLACEHOLDER)
                .organizerId(PLACEHOLDER)
                .title("Placeholder Activity")
                .tags(List.of("test"))
                .introduction("Placeholder introduction text")
                .startAt(now)
                .endAt(now.plusSeconds(7200))
                .pointLon(0.0)
                .pointLat(0.0)
                .city("Placeholder City")
                .address("Placeholder Address")
                .placeName("Placeholder Place")
                .safetyNotice("Placeholder safety notice")
                .capacity(10)
                .registrationDeadline(now.plusSeconds(3600))
                .reviewStatus(ActivityReviewStatus.pending)
                .runtimeStatus(ActivityRuntimeStatus.registering)
                .manualReviewRequired(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        activityRepository.save(activity);
    }

    public static void createPlaceholderTeamIfNeeded(
            TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        if (teamRepository.existsById(PLACEHOLDER)) {
            return;
        }
        Instant now = Instant.now();
        Team team = Team.builder()
                .teamId(PLACEHOLDER)
                .name("Placeholder Team")
                .tags(List.of("test"))
                .joinMode(TeamJoinMode.publicJoin)
                .capacity(20)
                .description("Placeholder team description")
                .status(TeamStatus.active)
                .creatorId(PLACEHOLDER)
                .leaderId(PLACEHOLDER)
                .chatId(PLACEHOLDER + "-chat")
                .createdAt(now)
                .updatedAt(now)
                .build();
        teamRepository.save(team);

        TeamMember member = TeamMember.builder()
                .memberId("placeholder-member")
                .teamId(PLACEHOLDER)
                .userId(PLACEHOLDER)
                .role(TeamMemberRole.leader)
                .points(0)
                .joinedAt(now)
                .build();
        teamMemberRepository.save(member);
    }

    public static void createPlaceholderReportIfNeeded(ReportRepository reportRepository) {
        if (reportRepository.existsById(PLACEHOLDER)) {
            return;
        }
        Instant now = Instant.now();
        Report report = Report.builder()
                .reportId(PLACEHOLDER)
                .reporterUserId(PLACEHOLDER)
                .targetType(ReportTargetType.user)
                .targetId(PLACEHOLDER)
                .reason("Placeholder report")
                .status(ReportStatus.pending)
                .createdAt(now)
                .build();
        reportRepository.save(report);
    }
}

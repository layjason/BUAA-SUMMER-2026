package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.ReportRepository;
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
class ReputationServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private PersonalProfileRepository personalProfileRepository;

    private ReputationService reputationService;

    private final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        reputationService = new ReputationService(reportRepository, personalProfileRepository);
    }

    @Nested
    @DisplayName("重新计算信誉分")
    class RecalculateScore {

        @Test
        @DisplayName("无举报时信誉分为 100")
        void shouldReturnDefaultWhenNoReports() {
            PersonalProfile profile = PersonalProfile.builder()
                    .userId(userId)
                    .reputationScore(100)
                    .build();

            when(personalProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(reportRepository.countByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, userId, ReportStatus.resolved))
                    .thenReturn(0L);
            when(reportRepository.countDistinctReporterByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, userId, ReportStatus.resolved))
                    .thenReturn(0L);

            int score = reputationService.recalculateScore(userId);

            assertThat(score).isEqualTo(100);
        }

        @Test
        @DisplayName("被举报 3 次且 2 个不同人，扣分 = 3×5 + 2×10 = 35，得分 65")
        void shouldCalculateCorrectScore() {
            PersonalProfile profile = PersonalProfile.builder()
                    .userId(userId)
                    .reputationScore(100)
                    .build();

            when(personalProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(reportRepository.countByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, userId, ReportStatus.resolved))
                    .thenReturn(3L);
            when(reportRepository.countDistinctReporterByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, userId, ReportStatus.resolved))
                    .thenReturn(2L);

            int score = reputationService.recalculateScore(userId);

            assertThat(score).isEqualTo(65);
        }

        @Test
        @DisplayName("扣分过多时最低为 0")
        void shouldFloorAtZero() {
            PersonalProfile profile =
                    PersonalProfile.builder().userId(userId).reputationScore(50).build();

            when(personalProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(reportRepository.countByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, userId, ReportStatus.resolved))
                    .thenReturn(20L);
            when(reportRepository.countDistinctReporterByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, userId, ReportStatus.resolved))
                    .thenReturn(10L);

            int score = reputationService.recalculateScore(userId);

            assertThat(score).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("活动报名权限")
    class ActivityRegistration {

        @Test
        @DisplayName("信誉分 >= 60 允许报名")
        void shouldAllowWhenScoreAboveThreshold() {
            PersonalProfile profile =
                    PersonalProfile.builder().userId(userId).reputationScore(65).build();

            when(personalProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

            assertThat(reputationService.canRegisterForActivity(userId)).isTrue();
        }

        @Test
        @DisplayName("信誉分 < 60 禁止报名")
        void shouldDenyWhenScoreBelowThreshold() {
            PersonalProfile profile =
                    PersonalProfile.builder().userId(userId).reputationScore(50).build();

            when(personalProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

            assertThat(reputationService.canRegisterForActivity(userId)).isFalse();
        }
    }
}

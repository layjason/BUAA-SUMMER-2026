package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;
import io.github.layjason.mayoistar.entity.social.ReputationRecord;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.ReportRepository;
import io.github.layjason.mayoistar.repository.ReputationRecordRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ReputationService 单元测试。
 *
 * <p>类职责：验证信誉分重算、报名阈值判断和积分变更流水审计逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ReputationServiceTest {

    @Mock
    private ReputationRecordRepository reputationRecordRepository;

    @Mock
    private PersonalProfileRepository personalProfileRepository;

    @Mock
    private ReportRepository reportRepository;

    private ReputationService service;

    @BeforeEach
    void setUp() {
        service = new ReputationServiceImpl(reputationRecordRepository, personalProfileRepository, reportRepository);
    }

    @Nested
    @DisplayName("重新计算信誉分")
    class RecalculateScoreTests {

        /**
         * 验证已处理举报数量和不同举报人数共同决定信誉分。
         */
        @Test
        @DisplayName("按照已处理举报统计重算信誉分")
        void recalculateByResolvedReports() {
            PersonalProfile profile = PersonalProfile.builder()
                    .userId("user-a")
                    .reputationScore(100)
                    .build();
            when(personalProfileRepository.findByUserId("user-a")).thenReturn(Optional.of(profile));
            when(reportRepository.countByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, "user-a", ReportStatus.resolved))
                    .thenReturn(3L);
            when(reportRepository.countDistinctReporterByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, "user-a", ReportStatus.resolved))
                    .thenReturn(2L);

            int score = service.recalculateScore("user-a");

            assertThat(score).isEqualTo(65);
            assertThat(profile.getReputationScore()).isEqualTo(65);
            verify(personalProfileRepository).save(profile);
        }

        /**
         * 验证过多举报不会把信誉分计算为负数。
         */
        @Test
        @DisplayName("信誉分最低为 0")
        void recalculateFloorsAtZero() {
            PersonalProfile profile = PersonalProfile.builder()
                    .userId("user-a")
                    .reputationScore(20)
                    .build();
            when(personalProfileRepository.findByUserId("user-a")).thenReturn(Optional.of(profile));
            when(reportRepository.countByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, "user-a", ReportStatus.resolved))
                    .thenReturn(30L);
            when(reportRepository.countDistinctReporterByTargetTypeAndTargetIdAndStatus(
                            ReportTargetType.user, "user-a", ReportStatus.resolved))
                    .thenReturn(10L);

            int score = service.recalculateScore("user-a");

            assertThat(score).isZero();
            assertThat(profile.getReputationScore()).isZero();
        }
    }

    @Nested
    @DisplayName("活动报名权限")
    class ActivityRegistrationTests {

        /**
         * 验证信誉分达到阈值时允许报名活动。
         */
        @Test
        @DisplayName("信誉分达到阈值时允许报名")
        void allowWhenScoreAboveThreshold() {
            PersonalProfile profile = PersonalProfile.builder()
                    .userId("user-a")
                    .reputationScore(60)
                    .build();
            when(personalProfileRepository.findByUserId("user-a")).thenReturn(Optional.of(profile));

            assertThat(service.canRegisterForActivity("user-a")).isTrue();
        }

        /**
         * 验证信誉分低于阈值时禁止报名活动。
         */
        @Test
        @DisplayName("信誉分低于阈值时禁止报名")
        void denyWhenScoreBelowThreshold() {
            PersonalProfile profile = PersonalProfile.builder()
                    .userId("user-a")
                    .reputationScore(59)
                    .build();
            when(personalProfileRepository.findByUserId("user-a")).thenReturn(Optional.of(profile));

            assertThat(service.canRegisterForActivity("user-a")).isFalse();
        }
    }

    @Nested
    @DisplayName("记录积分变更流水")
    class RecordScoreChangeTests {

        /**
         * 验证流水只保存审计记录，不直接累加修改当前信誉分。
         */
        @Test
        @DisplayName("保存流水但不直接更新信誉分")
        void recordAuditTrailOnly() {
            when(reputationRecordRepository.existsBySourceAndReferenceId(ReputationChangeSource.report, "rp-1"))
                    .thenReturn(false);

            service.recordScoreChange("user-a", -15, ReputationChangeSource.report, "rp-1", "举报核实扣分");

            ArgumentCaptor<ReputationRecord> captor = ArgumentCaptor.forClass(ReputationRecord.class);
            verify(reputationRecordRepository).save(captor.capture());

            ReputationRecord record = captor.getValue();
            assertThat(record.getUserId()).isEqualTo("user-a");
            assertThat(record.getScoreChange()).isEqualTo(-15);
            assertThat(record.getSource()).isEqualTo(ReputationChangeSource.report);
            assertThat(record.getReferenceId()).isEqualTo("rp-1");
            verify(personalProfileRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }

        /**
         * 验证同一来源和关联 ID 已有流水时不会重复保存。
         */
        @Test
        @DisplayName("同一举报只记录一次流水")
        void skipDuplicateReference() {
            when(reputationRecordRepository.existsBySourceAndReferenceId(ReputationChangeSource.report, "rp-1"))
                    .thenReturn(true);

            service.recordScoreChange("user-a", -15, ReputationChangeSource.report, "rp-1", "举报核实扣分");

            verify(reputationRecordRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }
    }
}

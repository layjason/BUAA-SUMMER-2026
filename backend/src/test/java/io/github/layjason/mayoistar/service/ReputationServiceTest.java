package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;
import io.github.layjason.mayoistar.entity.social.ReputationRecord;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
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
 * <p>类职责：验证积分变更记录和分数更新的业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ReputationServiceTest {

    @Mock
    private ReputationRecordRepository reputationRecordRepository;

    @Mock
    private PersonalProfileRepository personalProfileRepository;

    private ReputationService service;

    @BeforeEach
    void setUp() {
        service = new ReputationServiceImpl(reputationRecordRepository, personalProfileRepository);
    }

    @Nested
    @DisplayName("记录积分变更")
    class RecordScoreChangeTests {

        /**
         * 验证扣分操作正确持久化记录并更新用户信誉分。
         */
        @Test
        @DisplayName("扣分记录并更新用户信誉分")
        void deductScore() {
            PersonalProfile profile = PersonalProfile.builder()
                    .userId("user-a")
                    .reputationScore(100)
                    .build();
            when(personalProfileRepository.findById("user-a")).thenReturn(Optional.of(profile));

            service.recordScoreChange("user-a", -10, ReputationChangeSource.report, "rp-1", "举报核实扣分");

            ArgumentCaptor<ReputationRecord> captor = ArgumentCaptor.forClass(ReputationRecord.class);
            verify(reputationRecordRepository).save(captor.capture());

            ReputationRecord record = captor.getValue();
            assertThat(record.getUserId()).isEqualTo("user-a");
            assertThat(record.getScoreChange()).isEqualTo(-10);
            assertThat(record.getSource()).isEqualTo(ReputationChangeSource.report);
            assertThat(record.getReferenceId()).isEqualTo("rp-1");

            assertThat(profile.getReputationScore()).isEqualTo(90);
            verify(personalProfileRepository).save(profile);
        }

        /**
         * 验证加分操作正确更新信誉分。
         */
        @Test
        @DisplayName("加分记录并更新用户信誉分")
        void addScore() {
            PersonalProfile profile = PersonalProfile.builder()
                    .userId("user-a")
                    .reputationScore(80)
                    .build();
            when(personalProfileRepository.findById("user-a")).thenReturn(Optional.of(profile));

            service.recordScoreChange("user-a", 5, ReputationChangeSource.admin_manual, null, "管理员加分");

            assertThat(profile.getReputationScore()).isEqualTo(85);
            verify(personalProfileRepository).save(profile);
        }
    }
}

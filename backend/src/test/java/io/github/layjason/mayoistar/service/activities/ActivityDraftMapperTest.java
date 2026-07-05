package io.github.layjason.mayoistar.service.activities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.service.ai.AiContentReviewSnapshotMapper;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ActivityDraftMapper 测试")
class ActivityDraftMapperTest {

    private ActivityDraftMapper mapper;

    @BeforeEach
    void setUp() {
        MediaAccessService mediaAccessService = mock(MediaAccessService.class);
        AiContentReviewSnapshotMapper aiMapper = mock(AiContentReviewSnapshotMapper.class);
        mapper = new ActivityDraftMapper(mediaAccessService, aiMapper);
    }

    private Activity buildActivity(BigDecimal feeAmount) {
        return Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId("organizer-1")
                .title("测试活动")
                .tags(List.of("运动"))
                .introduction("活动介绍")
                .startAt(Instant.parse("2099-08-15T09:00:00Z"))
                .endAt(Instant.parse("2099-08-15T12:00:00Z"))
                .pointLon(116.397)
                .pointLat(39.908)
                .city("北京")
                .address("海淀区学院路37号")
                .placeName("北航体育馆")
                .capacity(30)
                .feeAmount(feeAmount)
                .reviewStatus(ActivityReviewStatus.draft)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .requireLocationCheck(false)
                .build();
    }

    @Nested
    @DisplayName("toDraftDetail")
    class ToDraftDetail {

        @Test
        @DisplayName("feeAmount 为 null 时应默认为 BigDecimal.ZERO")
        void shouldDefaultFeeAmountToZeroWhenNull() {
            Activity activity = buildActivity(null);

            ActivityDtos.ActivityDraftDetail detail = mapper.toDraftDetail(activity, Collections.emptyList(), id -> 0);

            assertThat(detail.getFeeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("feeAmount 不为 null 时应保持原值")
        void shouldPreserveFeeAmountWhenNotNull() {
            BigDecimal fee = new BigDecimal("50.00");
            Activity activity = buildActivity(fee);

            ActivityDtos.ActivityDraftDetail detail = mapper.toDraftDetail(activity, Collections.emptyList(), id -> 0);

            assertThat(detail.getFeeAmount()).isEqualByComparingTo(fee);
        }
    }

    @Nested
    @DisplayName("toActivityDetail")
    class ToActivityDetail {

        @Test
        @DisplayName("feeAmount 为 null 时应默认为 BigDecimal.ZERO")
        void shouldDefaultFeeAmountToZeroWhenNull() {
            Activity activity = buildActivity(null);

            ActivityDtos.ActivityDetail detail = mapper.toActivityDetail(
                    activity, "发起人", Collections.emptyList(), id -> 0, Collections.emptyList(), 0, 0, 0);

            assertThat(detail.getFeeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("feeAmount 不为 null 时应保持原值")
        void shouldPreserveFeeAmountWhenNotNull() {
            BigDecimal fee = new BigDecimal("88.88");
            Activity activity = buildActivity(fee);

            ActivityDtos.ActivityDetail detail = mapper.toActivityDetail(
                    activity, "发起人", Collections.emptyList(), id -> 0, Collections.emptyList(), 0, 0, 0);

            assertThat(detail.getFeeAmount()).isEqualByComparingTo(fee);
        }
    }
}

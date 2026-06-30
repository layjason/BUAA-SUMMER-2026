package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.service.activities.ActivityRuntimeScheduler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ActivityRuntimeSchedulerTests {

    @Autowired
    private ActivityRuntimeScheduler scheduler;

    @Test
    void computeTargetStatusShouldReturnEndedWhenEndAtPassed() {
        Activity activity =
                buildActivityWithTimes("2026-06-01T10:00:00Z", "2026-06-01T12:00:00Z", "2026-06-01T08:00:00Z");
        Instant now = Instant.parse("2026-06-01T13:00:00Z");

        assertThat(scheduler.computeTargetStatus(activity, now)).isEqualTo(ActivityRuntimeStatus.ended);
    }

    @Test
    void computeTargetStatusShouldReturnOngoingWhenStartedNotEnded() {
        Activity activity =
                buildActivityWithTimes("2026-06-01T10:00:00Z", "2026-06-01T12:00:00Z", "2026-06-01T08:00:00Z");
        Instant now = Instant.parse("2026-06-01T11:00:00Z");

        assertThat(scheduler.computeTargetStatus(activity, now)).isEqualTo(ActivityRuntimeStatus.ongoing);
    }

    @Test
    void computeTargetStatusShouldReturnRegistrationClosedWhenDeadlinePassed() {
        Activity activity =
                buildActivityWithTimes("2026-06-01T10:00:00Z", "2026-06-01T12:00:00Z", "2026-06-01T08:00:00Z");
        Instant now = Instant.parse("2026-06-01T09:00:00Z");

        assertThat(scheduler.computeTargetStatus(activity, now)).isEqualTo(ActivityRuntimeStatus.registrationClosed);
    }

    @Test
    void computeTargetStatusShouldReturnRegisteringWhenBeforeDeadline() {
        Activity activity =
                buildActivityWithTimes("2026-06-01T10:00:00Z", "2026-06-01T12:00:00Z", "2026-06-01T08:00:00Z");
        Instant now = Instant.parse("2026-06-01T07:00:00Z");

        assertThat(scheduler.computeTargetStatus(activity, now)).isEqualTo(ActivityRuntimeStatus.registering);
    }

    @Test
    void computeTargetStatusShouldReturnOngoingWhenExactlyAtStart() {
        Activity activity =
                buildActivityWithTimes("2026-06-01T10:00:00Z", "2026-06-01T12:00:00Z", "2026-06-01T08:00:00Z");
        Instant now = Instant.parse("2026-06-01T10:00:00Z");

        assertThat(scheduler.computeTargetStatus(activity, now)).isEqualTo(ActivityRuntimeStatus.ongoing);
    }

    @Test
    void computeTargetStatusShouldReturnEndedWhenExactlyAtEnd() {
        Activity activity =
                buildActivityWithTimes("2026-06-01T10:00:00Z", "2026-06-01T12:00:00Z", "2026-06-01T08:00:00Z");
        Instant now = Instant.parse("2026-06-01T12:00:00Z");

        assertThat(scheduler.computeTargetStatus(activity, now)).isEqualTo(ActivityRuntimeStatus.ended);
    }

    private Activity buildActivityWithTimes(String startAt, String endAt, String registrationDeadline) {
        return Activity.builder()
                .activityId("test-activity")
                .organizerId("test-user")
                .title("测试活动")
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .startAt(Instant.parse(startAt))
                .endAt(Instant.parse(endAt))
                .registrationDeadline(Instant.parse(registrationDeadline))
                .build();
    }
}

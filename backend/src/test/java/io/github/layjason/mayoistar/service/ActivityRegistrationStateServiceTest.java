package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.config.ActivityProperties;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReview;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityReviewRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({ActivityRegistrationStateService.class, ActivityRegistrationStateServiceTest.TestConfig.class})
class ActivityRegistrationStateServiceTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityRegistrationRepository activityRegistrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityReviewRepository activityReviewRepository;

    @Autowired
    private ActivityRegistrationStateService activityRegistrationStateService;

    private String organizerId;
    private String participantId;

    @BeforeEach
    void setUp() {
        activityRegistrationRepository.deleteAll();
        activityReviewRepository.deleteAll();
        activityRepository.deleteAll();
        userRepository.deleteAll();
        organizerId = createUser("organizer");
        participantId = createUser("participant");
    }

    @Test
    @DisplayName("未报名且活动报名中时可以发起报名")
    void shouldAllowRegistrationWhenOpenAndNoRegistration() {
        Activity activity =
                activity(ActivityRuntimeStatus.registering, Instant.now().plusSeconds(3600));
        activityRepository.save(activity);

        var result = activityRegistrationStateService.getParticipationState(activity.getActivityId(), participantId);

        assertThat(result.getCanRegister()).isTrue();
        assertThat(result.getStatus()).isNull();
        assertThat(result.getCanCancelRegistration()).isFalse();
        assertThat(result.getCanConfirmWaitingSeat()).isFalse();
        assertThat(result.getCanCheckIn()).isFalse();
    }

    @Test
    @DisplayName("已报名且活动报名中时可以取消")
    void shouldAllowCancelWhenRegisteredAndOpen() {
        Activity activity =
                activity(ActivityRuntimeStatus.registering, Instant.now().plusSeconds(3600));
        activityRepository.save(activity);
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), RegistrationStatus.registered, null, null));

        var result = activityRegistrationStateService.getParticipationState(activity.getActivityId(), participantId);

        assertThat(result.getCanRegister()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.registered);
        assertThat(result.getCanCancelRegistration()).isTrue();
    }

    @Test
    @DisplayName("候补待确认且确认窗口未过期时可以确认")
    void shouldAllowWaitingConfirmationBeforeDeadline() {
        Activity activity =
                activity(ActivityRuntimeStatus.registering, Instant.now().plusSeconds(3600));
        activityRepository.save(activity);
        Instant confirmationDeadline = Instant.now().plusSeconds(1800);
        activityRegistrationRepository.save(registration(
                activity.getActivityId(), RegistrationStatus.waitingConfirmation, 1, confirmationDeadline));

        var result = activityRegistrationStateService.getParticipationState(activity.getActivityId(), participantId);

        assertThat(result.getCanRegister()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.waitingConfirmation);
        assertThat(result.getWaitingRank()).isEqualTo(1);
        assertThat(result.getConfirmationDeadline()).isEqualTo(confirmationDeadline.toString());
        assertThat(result.getCanConfirmWaitingSeat()).isTrue();
    }

    @Test
    @DisplayName("报名截止后不可报名或取消")
    void shouldDisallowRegistrationAndCancelAfterDeadline() {
        Activity activity =
                activity(ActivityRuntimeStatus.registering, Instant.now().minusSeconds(3600));
        activityRepository.save(activity);
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), RegistrationStatus.registered, null, null));

        var result = activityRegistrationStateService.getParticipationState(activity.getActivityId(), participantId);

        assertThat(result.getCanRegister()).isFalse();
        assertThat(result.getCanCancelRegistration()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.registered);
    }

    @Test
    @DisplayName("活动进行中且已报名时可以签到")
    void shouldAllowCheckInWhenActivityOngoingAndRegistered() {
        Activity activity =
                activity(ActivityRuntimeStatus.ongoing, Instant.now().minusSeconds(3600));
        activityRepository.save(activity);
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), RegistrationStatus.registered, null, null));

        var result = activityRegistrationStateService.getParticipationState(activity.getActivityId(), participantId);

        assertThat(result.getCanRegister()).isFalse();
        assertThat(result.getCanCancelRegistration()).isFalse();
        assertThat(result.getCanCheckIn()).isTrue();
    }

    @Test
    @DisplayName("不可见活动返回不可操作状态")
    void shouldReturnClosedStateWhenActivityNotVisible() {
        Activity activity =
                activity(ActivityRuntimeStatus.takenDown, Instant.now().plusSeconds(3600));
        activityRepository.save(activity);

        var result = activityRegistrationStateService.getParticipationState(activity.getActivityId(), participantId);

        assertThat(result.getCanRegister()).isFalse();
        assertThat(result.getStatus()).isNull();
        assertThat(result.getCanCancelRegistration()).isFalse();
        assertThat(result.getCanConfirmWaitingSeat()).isFalse();
        assertThat(result.getCanCheckIn()).isFalse();
        assertThat(result.getCanReview()).isFalse();
    }

    @Test
    @DisplayName("活动已结束且已签到且未评价时可以评价")
    void shouldAllowReviewWhenActivityEndedAndCheckedInAndNotReviewed() {
        Instant now = Instant.now();
        Activity activity = Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title("已结束活动")
                .tags(List.of("桌游"))
                .introduction("活动简介")
                .startAt(now.minusSeconds(14400))
                .endAt(now.minusSeconds(7200))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("北京地址")
                .placeName("北京地点")
                .safetyNotice("注意安全")
                .capacity(20)
                .feeAmount(BigDecimal.ZERO)
                .registrationDeadline(now.minusSeconds(36000))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.ended)
                .manualReviewRequired(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        activityRepository.save(activity);
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), RegistrationStatus.checkedIn, null, null));

        var result = activityRegistrationStateService.getParticipationState(activity.getActivityId(), participantId);

        assertThat(result.getCanReview()).isTrue();
        assertThat(result.getReviewWindowEndsAt()).isNotNull();
    }

    @Test
    @DisplayName("已评价后不能再评价")
    void shouldDisallowReviewWhenAlreadyReviewed() {
        Instant now = Instant.now();
        Activity activity = Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title("已结束活动")
                .tags(List.of("桌游"))
                .introduction("活动简介")
                .startAt(now.minusSeconds(14400))
                .endAt(now.minusSeconds(7200))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("北京地址")
                .placeName("北京地点")
                .safetyNotice("注意安全")
                .capacity(20)
                .feeAmount(BigDecimal.ZERO)
                .registrationDeadline(now.minusSeconds(36000))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.ended)
                .manualReviewRequired(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        activityRepository.save(activity);
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), RegistrationStatus.checkedIn, null, null));
        activityReviewRepository.save(ActivityReview.builder()
                .reviewId(UUID.randomUUID().toString())
                .activityId(activity.getActivityId())
                .userId(participantId)
                .rating(5)
                .createdAt(Instant.now())
                .build());

        var result = activityRegistrationStateService.getParticipationState(activity.getActivityId(), participantId);

        assertThat(result.getCanReview()).isFalse();
    }

    @Test
    @DisplayName("评价窗口过期后不能评价")
    void shouldDisallowReviewWhenWindowExpired() {
        Instant now = Instant.now();
        Activity activity = Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title("很久以前结束的活动")
                .tags(List.of("桌游"))
                .introduction("活动简介")
                .startAt(now.minusSeconds(864000))
                .endAt(now.minusSeconds(864000 - 7200))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("北京地址")
                .placeName("北京地点")
                .safetyNotice("注意安全")
                .capacity(20)
                .feeAmount(BigDecimal.ZERO)
                .registrationDeadline(now.minusSeconds(864000))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.ended)
                .manualReviewRequired(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        activityRepository.save(activity);
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), RegistrationStatus.checkedIn, null, null));

        var result = activityRegistrationStateService.getParticipationState(activity.getActivityId(), participantId);

        assertThat(result.getCanReview()).isFalse();
    }

    private String createUser(String nicknamePrefix) {
        String userId = UUID.randomUUID().toString();
        userRepository.save(User.builder()
                .userId(userId)
                .email(nicknamePrefix + "-" + userId + "@example.com")
                .nickname(nicknamePrefix + "-" + userId.substring(0, 8))
                .passwordHash("password-hash")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .activatedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        return userId;
    }

    private Activity activity(ActivityRuntimeStatus runtimeStatus, Instant registrationDeadline) {
        Instant startAt = Instant.now().plusSeconds(7200);
        return Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title("活动")
                .tags(List.of("桌游"))
                .introduction("活动简介")
                .startAt(startAt)
                .endAt(startAt.plusSeconds(7200))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("北京地址")
                .placeName("北京地点")
                .safetyNotice("注意安全")
                .capacity(20)
                .feeAmount(BigDecimal.ZERO)
                .registrationDeadline(registrationDeadline)
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(runtimeStatus)
                .manualReviewRequired(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private ActivityRegistration registration(
            String activityId, RegistrationStatus status, Integer waitingRank, Instant confirmationDeadline) {
        return ActivityRegistration.builder()
                .registrationId(UUID.randomUUID().toString())
                .activityId(activityId)
                .userId(participantId)
                .status(status)
                .participantNote("备注")
                .acceptedSafetyNotice(true)
                .waitingRank(waitingRank)
                .confirmationDeadline(confirmationDeadline)
                .registeredAt(Instant.now())
                .build();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ActivityProperties activityProperties() {
            return new ActivityProperties();
        }
    }
}

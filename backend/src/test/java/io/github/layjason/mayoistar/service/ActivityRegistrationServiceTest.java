package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(ActivityRegistrationService.class)
class ActivityRegistrationServiceTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityRegistrationRepository activityRegistrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityRegistrationService activityRegistrationService;

    @MockBean
    private ReputationService reputationService;

    private String organizerId;
    private String participantId;

    @BeforeEach
    void setUp() {
        activityRegistrationRepository.deleteAll();
        activityRepository.deleteAll();
        userRepository.deleteAll();
        organizerId = createUser("organizer");
        participantId = createUser("participant");
        when(reputationService.canRegisterForActivity(any())).thenReturn(true);
    }

    @Test
    @DisplayName("容量未满时确认报名为正式报名")
    void shouldRegisterWhenSeatAvailable() {
        Activity activity = activity(2);
        activityRepository.save(activity);

        var result = activityRegistrationService.registerActivity(
                activity.getActivityId(), participantId, registerRequest(true));

        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.registered);
        assertThat(result.getWaitingRank()).isNull();
        assertThat(result.getConfirmationDeadline()).isNull();
        ActivityRegistration saved = findRegistration(activity.getActivityId(), participantId);
        assertThat(saved.getParticipantNote()).isEqualTo("备注");
        assertThat(saved.getAcceptedSafetyNotice()).isTrue();
    }

    @Test
    @DisplayName("满员时进入候补队列")
    void shouldJoinWaitingQueueWhenFull() {
        Activity activity = activity(1);
        activityRepository.save(activity);
        activityRegistrationRepository.save(registration(
                activity.getActivityId(), createUser("registered"), RegistrationStatus.registered, null, null));

        var result = activityRegistrationService.registerActivity(
                activity.getActivityId(), participantId, registerRequest(true));

        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.waiting);
        assertThat(result.getWaitingRank()).isEqualTo(1);
        assertThat(result.getConfirmationDeadline()).isNull();
    }

    @Test
    @DisplayName("候补确认中的名额会被视为已占用")
    void shouldTreatWaitingConfirmationAsOccupiedSeat() {
        Activity activity = activity(1);
        activityRepository.save(activity);
        activityRegistrationRepository.save(registration(
                activity.getActivityId(),
                createUser("waiting-confirmation"),
                RegistrationStatus.waitingConfirmation,
                null,
                Instant.now().plusSeconds(900)));

        var result = activityRegistrationService.registerActivity(
                activity.getActivityId(), participantId, registerRequest(true));

        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.waiting);
        assertThat(result.getWaitingRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("已存在有效报名时禁止重复报名")
    void shouldRejectDuplicateRegistration() {
        Activity activity = activity(2);
        activityRepository.save(activity);
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), participantId, RegistrationStatus.registered, null, null));

        assertThatThrownBy(() -> activityRegistrationService.registerActivity(
                        activity.getActivityId(), participantId, registerRequest(true)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(20007);
    }

    @Test
    @DisplayName("取消过的用户可以再次报名并复用原报名记录")
    void shouldReuseCanceledRegistrationWhenRegisteringAgain() {
        Activity activity = activity(2);
        activityRepository.save(activity);
        ActivityRegistration canceled = activityRegistrationRepository.save(
                registration(activity.getActivityId(), participantId, RegistrationStatus.canceled, null, null));

        var result = activityRegistrationService.registerActivity(
                activity.getActivityId(), participantId, registerRequest(true));

        assertThat(result.getRegistrationId()).isEqualTo(canceled.getRegistrationId());
        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.registered);
        assertThat(activityRegistrationRepository.findAll())
                .filteredOn(registration -> registration.getActivityId().equals(activity.getActivityId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("未接受安全须知时返回业务错误码")
    void shouldRejectRegistrationWithoutSafetyNotice() {
        Activity activity = activity(2);
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityRegistrationService.registerActivity(
                        activity.getActivityId(), participantId, registerRequest(false)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(20010);
    }

    @Test
    @DisplayName("取消正式报名后候补队首进入待确认")
    void shouldPromoteFirstWaitingUserWhenRegisteredUserCancels() {
        Activity activity = activity(1);
        activityRepository.save(activity);
        String firstWaitingUserId = createUser("waiting-1");
        String secondWaitingUserId = createUser("waiting-2");
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), participantId, RegistrationStatus.registered, null, null));
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), firstWaitingUserId, RegistrationStatus.waiting, 1, null));
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), secondWaitingUserId, RegistrationStatus.waiting, 2, null));

        var result = activityRegistrationService.cancelRegistration(activity.getActivityId(), participantId);

        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.canceled);
        ActivityRegistration promoted = findRegistration(activity.getActivityId(), firstWaitingUserId);
        assertThat(promoted.getStatus()).isEqualTo(RegistrationStatus.waitingConfirmation);
        assertThat(promoted.getWaitingRank()).isNull();
        assertThat(promoted.getConfirmationDeadline()).isNotNull();
        ActivityRegistration secondWaiting = findRegistration(activity.getActivityId(), secondWaitingUserId);
        assertThat(secondWaiting.getStatus()).isEqualTo(RegistrationStatus.waiting);
        assertThat(secondWaiting.getWaitingRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("取消候补后剩余候补按顺序重排")
    void shouldRerankWaitingQueueWhenWaitingUserCancels() {
        Activity activity = activity(1);
        activityRepository.save(activity);
        String secondWaitingUserId = createUser("waiting-2");
        String thirdWaitingUserId = createUser("waiting-3");
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), participantId, RegistrationStatus.waiting, 1, null));
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), secondWaitingUserId, RegistrationStatus.waiting, 2, null));
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), thirdWaitingUserId, RegistrationStatus.waiting, 3, null));

        var result = activityRegistrationService.cancelRegistration(activity.getActivityId(), participantId);

        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.canceled);
        assertThat(findRegistration(activity.getActivityId(), secondWaitingUserId)
                        .getWaitingRank())
                .isEqualTo(1);
        assertThat(findRegistration(activity.getActivityId(), thirdWaitingUserId)
                        .getWaitingRank())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("候补确认同意后转为正式报名")
    void shouldRegisterWhenWaitingConfirmationAccepted() {
        Activity activity = activity(1);
        activityRepository.save(activity);
        activityRegistrationRepository.save(registration(
                activity.getActivityId(),
                participantId,
                RegistrationStatus.waitingConfirmation,
                null,
                Instant.now().plusSeconds(900)));

        var result = activityRegistrationService.confirmWaitingSeat(
                activity.getActivityId(), participantId, waitingConfirmationRequest(true));

        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.registered);
        assertThat(result.getWaitingRank()).isNull();
        assertThat(result.getConfirmationDeadline()).isNull();
    }

    @Test
    @DisplayName("候补确认拒绝后取消当前记录并递补下一位")
    void shouldPromoteNextWaitingUserWhenWaitingConfirmationRejected() {
        Activity activity = activity(1);
        activityRepository.save(activity);
        String nextWaitingUserId = createUser("waiting-next");
        activityRegistrationRepository.save(registration(
                activity.getActivityId(),
                participantId,
                RegistrationStatus.waitingConfirmation,
                null,
                Instant.now().plusSeconds(900)));
        activityRegistrationRepository.save(
                registration(activity.getActivityId(), nextWaitingUserId, RegistrationStatus.waiting, 1, null));

        var result = activityRegistrationService.confirmWaitingSeat(
                activity.getActivityId(), participantId, waitingConfirmationRequest(false));

        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.canceled);
        ActivityRegistration promoted = findRegistration(activity.getActivityId(), nextWaitingUserId);
        assertThat(promoted.getStatus()).isEqualTo(RegistrationStatus.waitingConfirmation);
        assertThat(promoted.getConfirmationDeadline()).isNotNull();
    }

    private ActivityDtos.RegisterActivityRequest registerRequest(boolean acceptedSafetyNotice) {
        ActivityDtos.RegisterActivityRequest request = new ActivityDtos.RegisterActivityRequest();
        request.setParticipantNote("备注");
        request.setAcceptedSafetyNotice(acceptedSafetyNotice);
        return request;
    }

    private ActivityDtos.WaitingConfirmationRequest waitingConfirmationRequest(boolean confirmed) {
        ActivityDtos.WaitingConfirmationRequest request = new ActivityDtos.WaitingConfirmationRequest();
        request.setConfirmed(confirmed);
        return request;
    }

    private ActivityRegistration findRegistration(String activityId, String userId) {
        return activityRegistrationRepository
                .findByActivityIdAndUserId(activityId, userId)
                .orElseThrow();
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

    private Activity activity(int capacity) {
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
                .capacity(capacity)
                .feeAmount(BigDecimal.ZERO)
                .registrationDeadline(Instant.now().plusSeconds(3600))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.registering)
                .manualReviewRequired(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private ActivityRegistration registration(
            String activityId,
            String userId,
            RegistrationStatus status,
            Integer waitingRank,
            Instant confirmationDeadline) {
        return ActivityRegistration.builder()
                .registrationId(UUID.randomUUID().toString())
                .activityId(activityId)
                .userId(userId)
                .status(status)
                .participantNote("备注")
                .acceptedSafetyNotice(true)
                .waitingRank(waitingRank)
                .confirmationDeadline(confirmationDeadline)
                .registeredAt(Instant.now())
                .build();
    }
}

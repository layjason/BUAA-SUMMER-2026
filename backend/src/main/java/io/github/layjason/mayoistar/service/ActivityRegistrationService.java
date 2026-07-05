package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityRegistrationService {

    private static final int ACTIVITY_NOT_VISIBLE = 20002;
    private static final int REGISTRATION_CLOSED = 20006;
    private static final int DUPLICATE_REGISTRATION = 20007;
    private static final int REPUTATION_INSUFFICIENT = 20008;
    private static final int AGE_REQUIREMENT_NOT_MET = ErrorCodes.AGE_REQUIREMENT_NOT_MET;
    private static final int SAFETY_NOTICE_NOT_ACCEPTED = 20010;
    private static final int REGISTRATION_NOT_FOUND = 20011;
    private static final int WAITING_CONFIRMATION_UNAVAILABLE = 20012;
    private static final Duration WAITING_CONFIRMATION_WINDOW = Duration.ofMinutes(15);
    private static final Set<RegistrationStatus> OCCUPIED_STATUSES =
            Set.of(RegistrationStatus.registered, RegistrationStatus.waitingConfirmation, RegistrationStatus.checkedIn);

    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ReputationService reputationService;
    private final PersonalProfileRepository personalProfileRepository;

    @Transactional
    public ActivityDtos.RegistrationResult registerActivity(
            String activityId, String userId, ActivityDtos.RegisterActivityRequest request) {
        Instant now = Instant.now();
        if (request == null || !Boolean.TRUE.equals(request.getAcceptedSafetyNotice())) {
            throw new BusinessException(SAFETY_NOTICE_NOT_ACCEPTED, "Safety notice must be accepted");
        }
        if (!reputationService.canRegisterForActivity(userId)) {
            throw new BusinessException(
                    REPUTATION_INSUFFICIENT, "Reputation score is insufficient for activity registration");
        }
        Activity activity = findVisibleActivityForUpdate(activityId);
        if (!isRegistrationOpen(activity, now)) {
            throw new BusinessException(REGISTRATION_CLOSED, "Activity registration is closed");
        }
        if (activity.getMinAge() != null) {
            checkAgeRequirement(userId, activity.getMinAge(), now);
        }

        ActivityRegistration registration = activityRegistrationRepository
                .findByActivityIdAndUserId(activityId, userId)
                .orElseGet(() -> newRegistration(activityId, userId));
        if (registration.getStatus() != null && registration.getStatus() != RegistrationStatus.canceled) {
            throw new BusinessException(DUPLICATE_REGISTRATION, "Registration already exists");
        }

        registration.setParticipantNote(request.getParticipantNote());
        registration.setAcceptedSafetyNotice(true);
        registration.setCheckedInAt(null);
        registration.setRegisteredAt(now);
        if (hasAvailableSeat(activity)) {
            markRegistered(registration);
        } else {
            markWaiting(registration, nextWaitingRank(activityId));
        }
        return toResult(activityRegistrationRepository.save(registration));
    }

    @Transactional
    public ActivityDtos.RegistrationResult cancelRegistration(String activityId, String userId) {
        Instant now = Instant.now();
        Activity activity = findVisibleActivityForUpdate(activityId);
        if (!isRegistrationOpen(activity, now)) {
            throw new BusinessException(REGISTRATION_CLOSED, "Activity registration is closed");
        }
        ActivityRegistration registration = activityRegistrationRepository
                .findByActivityIdAndUserId(activityId, userId)
                .filter(this::canCancel)
                .orElseThrow(() -> new BusinessException(REGISTRATION_NOT_FOUND, "Registration does not exist"));

        RegistrationStatus previousStatus = registration.getStatus();
        markCanceled(registration);
        activityRegistrationRepository.save(registration);

        if (previousStatus == RegistrationStatus.waiting) {
            rerankWaitingQueue(activityId);
        } else {
            promoteNextWaiting(activityId, now);
        }
        return toResult(registration);
    }

    @Transactional
    public ActivityDtos.RegistrationResult confirmWaitingSeat(
            String activityId, String userId, ActivityDtos.WaitingConfirmationRequest request) {
        Instant now = Instant.now();
        findVisibleActivityForUpdate(activityId);
        ActivityRegistration registration = activityRegistrationRepository
                .findByActivityIdAndUserId(activityId, userId)
                .filter(candidate -> canConfirmWaitingSeat(candidate, now))
                .orElseThrow(() ->
                        new BusinessException(WAITING_CONFIRMATION_UNAVAILABLE, "Waiting confirmation is unavailable"));

        if (request != null && Boolean.TRUE.equals(request.getConfirmed())) {
            markRegistered(registration);
        } else {
            markCanceled(registration);
            promoteNextWaiting(activityId, now);
        }
        return toResult(activityRegistrationRepository.save(registration));
    }

    /**
     * 处理所有已过确认截止时间的候补待确认记录。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：所有过期的候补待确认记录被取消，对应的名额顺延给下一位候补者。
     *
     * <p>不变量：每个活动最多处理一次；已取消的候补记录不会重复处理。
     *
     * @param now 当前时间
     */
    @Transactional
    public void processExpiredWaitingConfirmations(Instant now) {
        List<ActivityRegistration> expiredRegistrations =
                activityRegistrationRepository.findByStatusAndConfirmationDeadlineBefore(
                        RegistrationStatus.waitingConfirmation, now);
        if (expiredRegistrations.isEmpty()) {
            return;
        }
        // 按活动分组，每个活动加锁处理
        expiredRegistrations.stream()
                .collect(Collectors.groupingBy(ActivityRegistration::getActivityId))
                .forEach((activityId, registrations) -> {
                    try {
                        Activity activity = findVisibleActivityForUpdate(activityId);
                        if (!isRegistrationOpen(activity, now)) {
                            log.debug("活动报名已关闭，跳过过期候补确认处理: activityId={}", activityId);
                            return;
                        }
                        for (ActivityRegistration registration : registrations) {
                            // 重新加载以确保状态一致性
                            activityRegistrationRepository
                                    .findByActivityIdAndUserId(activityId, registration.getUserId())
                                    .filter(r -> r.getStatus() == RegistrationStatus.waitingConfirmation
                                            && r.getConfirmationDeadline() != null
                                            && !r.getConfirmationDeadline().isAfter(now))
                                    .ifPresent(r -> {
                                        markCanceled(r);
                                        activityRegistrationRepository.save(r);
                                        log.info(
                                                "候补确认超时自动取消: activityId={}, userId={}, registrationId={}",
                                                activityId,
                                                r.getUserId(),
                                                r.getRegistrationId());
                                    });
                        }
                        // 每个被取消的名额需要顺延给下一位候补
                        int canceledCount = registrations.size();
                        for (int i = 0; i < canceledCount; i++) {
                            promoteNextWaiting(activityId, now);
                        }
                    } catch (BusinessException e) {
                        log.warn("处理过期候补确认失败: activityId={}", activityId, e);
                    }
                });
    }

    private Activity findVisibleActivityForUpdate(String activityId) {
        return activityRepository
                .findByIdForUpdate(activityId)
                .filter(this::isVisible)
                .orElseThrow(() -> new BusinessException(ACTIVITY_NOT_VISIBLE, "Activity {activityId} is not visible"));
    }

    private boolean isVisible(Activity activity) {
        return activity.getReviewStatus() == ActivityReviewStatus.approved
                && activity.getRuntimeStatus() != ActivityRuntimeStatus.takenDown;
    }

    private boolean isRegistrationOpen(Activity activity, Instant now) {
        return activity.getRuntimeStatus() == ActivityRuntimeStatus.registering
                && (activity.getRegistrationDeadline() == null
                        || !activity.getRegistrationDeadline().isBefore(now));
    }

    /**
     * 校验用户年龄是否满足活动最低年龄要求。
     *
     * <p>前置条件：minAge 不为 null；userId 对应的用户已存在。
     *
     * <p>后置条件：若用户未设置生日或年龄不足，抛出 AGE_REQUIREMENT_NOT_MET 异常。
     *
     * <p>不变量：用户资料和活动信息不被修改。
     *
     * @param userId 用户 ID
     * @param minAge 活动最低年龄要求
     * @param now    当前时间，用于计算年龄
     */
    private void checkAgeRequirement(String userId, int minAge, Instant now) {
        String birthday = personalProfileRepository
                .findById(userId)
                .map(profile -> profile.getBirthday())
                .orElse(null);
        if (birthday == null || birthday.isBlank()) {
            throw new BusinessException(AGE_REQUIREMENT_NOT_MET, "Age requirement not met: birthday is not set");
        }
        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(birthday);
        } catch (Exception e) {
            log.warn("用户生日格式无效: userId={}, birthday={}", userId, birthday);
            throw new BusinessException(AGE_REQUIREMENT_NOT_MET, "Age requirement not met: invalid birthday format");
        }
        int age = Period.between(birthDate, LocalDate.ofInstant(now, java.time.ZoneOffset.UTC))
                .getYears();
        if (age < minAge) {
            throw new BusinessException(AGE_REQUIREMENT_NOT_MET, "Age requirement not met: minimum age is " + minAge);
        }
    }

    private boolean hasAvailableSeat(Activity activity) {
        long occupiedCount = activityRegistrationRepository.countByActivityIdAndStatusIn(
                activity.getActivityId(), OCCUPIED_STATUSES);
        return occupiedCount < activity.getCapacity();
    }

    private ActivityRegistration newRegistration(String activityId, String userId) {
        ActivityRegistration registration = new ActivityRegistration();
        registration.setRegistrationId(UUID.randomUUID().toString());
        registration.setActivityId(activityId);
        registration.setUserId(userId);
        return registration;
    }

    private void markRegistered(ActivityRegistration registration) {
        registration.setStatus(RegistrationStatus.registered);
        registration.setWaitingRank(null);
        registration.setConfirmationDeadline(null);
    }

    private void markWaiting(ActivityRegistration registration, int waitingRank) {
        registration.setStatus(RegistrationStatus.waiting);
        registration.setWaitingRank(waitingRank);
        registration.setConfirmationDeadline(null);
    }

    private void markWaitingConfirmation(ActivityRegistration registration, Instant now) {
        registration.setStatus(RegistrationStatus.waitingConfirmation);
        registration.setWaitingRank(null);
        registration.setConfirmationDeadline(now.plus(WAITING_CONFIRMATION_WINDOW));
    }

    private void markCanceled(ActivityRegistration registration) {
        registration.setStatus(RegistrationStatus.canceled);
        registration.setWaitingRank(null);
        registration.setConfirmationDeadline(null);
    }

    private boolean canCancel(ActivityRegistration registration) {
        return registration.getStatus() == RegistrationStatus.registered
                || registration.getStatus() == RegistrationStatus.waiting
                || registration.getStatus() == RegistrationStatus.waitingConfirmation;
    }

    private boolean canConfirmWaitingSeat(ActivityRegistration registration, Instant now) {
        return registration.getStatus() == RegistrationStatus.waitingConfirmation
                && registration.getConfirmationDeadline() != null
                && !registration.getConfirmationDeadline().isBefore(now);
    }

    private int nextWaitingRank(String activityId) {
        return activityRegistrationRepository
                        .findByActivityIdAndStatusOrderByWaitingRankAscRegisteredAtAsc(
                                activityId, RegistrationStatus.waiting)
                        .size()
                + 1;
    }

    private void promoteNextWaiting(String activityId, Instant now) {
        activityRegistrationRepository
                .findFirstByActivityIdAndStatusOrderByWaitingRankAscRegisteredAtAsc(
                        activityId, RegistrationStatus.waiting)
                .ifPresent(registration -> {
                    markWaitingConfirmation(registration, now);
                    activityRegistrationRepository.save(registration);
                    rerankWaitingQueue(activityId);
                });
    }

    private void rerankWaitingQueue(String activityId) {
        List<ActivityRegistration> waitingQueue =
                activityRegistrationRepository.findByActivityIdAndStatusOrderByWaitingRankAscRegisteredAtAsc(
                        activityId, RegistrationStatus.waiting);
        for (int index = 0; index < waitingQueue.size(); index++) {
            waitingQueue.get(index).setWaitingRank(index + 1);
        }
    }

    private ActivityDtos.RegistrationResult toResult(ActivityRegistration registration) {
        ActivityDtos.RegistrationResult dto = new ActivityDtos.RegistrationResult();
        dto.setRegistrationId(registration.getRegistrationId());
        dto.setActivityId(registration.getActivityId());
        dto.setStatus(registration.getStatus());
        dto.setWaitingRank(registration.getWaitingRank());
        dto.setConfirmationDeadline(formatInstant(registration.getConfirmationDeadline()));
        return dto;
    }

    private String formatInstant(Instant value) {
        return value == null ? null : value.toString();
    }
}

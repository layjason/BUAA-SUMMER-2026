package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.config.ActivityProperties;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityReviewRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityRegistrationStateService {

    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityReviewRepository activityReviewRepository;
    private final ActivityProperties activityProperties;

    @Transactional(readOnly = true)
    public ActivityDtos.ActivityParticipationState getParticipationState(String activityId, String userId) {
        return activityRepository
                .findById(activityId)
                .filter(this::isVisible)
                .map(activity -> toParticipationState(
                        activity,
                        activityRegistrationRepository
                                .findByActivityIdAndUserId(activityId, userId)
                                .orElse(null),
                        Instant.now()))
                .orElseGet(this::closedState);
    }

    private ActivityDtos.ActivityParticipationState toParticipationState(
            Activity activity, ActivityRegistration registration, Instant now) {
        ActivityDtos.ActivityParticipationState dto = new ActivityDtos.ActivityParticipationState();
        boolean registrationOpen = isRegistrationOpen(activity, now);
        if (registration != null) {
            dto.setStatus(registration.getStatus());
            dto.setWaitingRank(registration.getWaitingRank());
            dto.setConfirmationDeadline(formatInstant(registration.getConfirmationDeadline()));
        }
        dto.setCanRegister(registrationOpen && canStartRegistration(registration));
        dto.setCanCancelRegistration(registrationOpen && canCancel(registration));
        dto.setCanConfirmWaitingSeat(canConfirmWaitingSeat(registration, now));
        dto.setCanCheckIn(canCheckIn(activity, registration));
        boolean canReview = canReview(activity, registration, now);
        dto.setCanReview(canReview);
        Instant reviewWindowEnds = computeReviewWindowEnds(activity);
        if (canReview || reviewWindowEnds != null) {
            dto.setReviewWindowEndsAt(formatInstant(reviewWindowEnds));
        }
        return dto;
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

    private boolean canStartRegistration(ActivityRegistration registration) {
        return registration == null || registration.getStatus() == RegistrationStatus.canceled;
    }

    private boolean canCancel(ActivityRegistration registration) {
        if (registration == null) {
            return false;
        }
        return registration.getStatus() == RegistrationStatus.registered
                || registration.getStatus() == RegistrationStatus.waiting
                || registration.getStatus() == RegistrationStatus.waitingConfirmation;
    }

    private boolean canConfirmWaitingSeat(ActivityRegistration registration, Instant now) {
        return registration != null
                && registration.getStatus() == RegistrationStatus.waitingConfirmation
                && registration.getConfirmationDeadline() != null
                && !registration.getConfirmationDeadline().isBefore(now);
    }

    private boolean canCheckIn(Activity activity, ActivityRegistration registration) {
        return activity.getRuntimeStatus() == ActivityRuntimeStatus.ongoing
                && registration != null
                && registration.getStatus() == RegistrationStatus.registered;
    }

    /**
     * 判断当前用户是否可以评价该活动。
     *
     * <p>前置条件：activity 和 now 非空。
     *
     * <p>后置条件：仅当活动已结束、用户已签到、尚未评价且评价窗口未过期时返回 true。
     *
     * @param activity     活动实体
     * @param registration 用户报名记录，可为 null
     * @param now          当前时间
     * @return 是否可以评价
     */
    private boolean canReview(Activity activity, ActivityRegistration registration, Instant now) {
        // 活动未结束
        if (!isActivityEnded(activity, now)) {
            return false;
        }
        // 用户未签到
        if (registration == null || registration.getStatus() != RegistrationStatus.checkedIn) {
            return false;
        }
        // 已评价
        if (activityReviewRepository.existsByActivityIdAndUserId(activity.getActivityId(), registration.getUserId())) {
            return false;
        }
        // 评价窗口已过期
        Instant reviewWindowEnds = computeReviewWindowEnds(activity);
        if (reviewWindowEnds != null && !now.isBefore(reviewWindowEnds)) {
            return false;
        }
        return true;
    }

    /**
     * 计算评价窗口关闭时间。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：返回活动结束时间 + 评价窗口天数；若活动未设置结束时间，返回 null。
     *
     * @param activity 活动实体
     * @return 评价窗口关闭时间，无法计算时返回 null
     */
    @Nullable
    private Instant computeReviewWindowEnds(Activity activity) {
        if (activity.getEndAt() == null) {
            return null;
        }
        return activity.getEndAt().plus(Duration.ofDays(activityProperties.getReviewWindowDays()));
    }

    /**
     * 判断活动是否已结束。
     */
    private boolean isActivityEnded(Activity activity, Instant now) {
        if (activity.getRuntimeStatus() == ActivityRuntimeStatus.ended) {
            return true;
        }
        return activity.getEndAt() != null && !activity.getEndAt().isAfter(now);
    }

    private ActivityDtos.ActivityParticipationState closedState() {
        ActivityDtos.ActivityParticipationState dto = new ActivityDtos.ActivityParticipationState();
        dto.setCanRegister(false);
        dto.setCanCancelRegistration(false);
        dto.setCanConfirmWaitingSeat(false);
        dto.setCanCheckIn(false);
        dto.setCanReview(false);
        return dto;
    }

    private String formatInstant(Instant value) {
        return value == null ? null : value.toString();
    }
}

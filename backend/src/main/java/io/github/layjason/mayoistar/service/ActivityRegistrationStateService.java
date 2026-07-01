package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityRegistrationStateService {

    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;

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

    private ActivityDtos.ActivityParticipationState closedState() {
        ActivityDtos.ActivityParticipationState dto = new ActivityDtos.ActivityParticipationState();
        dto.setCanRegister(false);
        dto.setCanCancelRegistration(false);
        dto.setCanConfirmWaitingSeat(false);
        dto.setCanCheckIn(false);
        return dto;
    }

    private String formatInstant(Instant value) {
        return value == null ? null : value.toString();
    }
}

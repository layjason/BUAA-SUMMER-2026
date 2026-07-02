package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动报名计数读取服务。
 *
 * <p>类职责：集中维护 ActivitySummary/ActivityDetail 使用的报名人数、占用名额数和候补人数统计口径。
 *
 * <p>类不变量：只读聚合报名记录，不修改持久化数据。
 */
@Service
@RequiredArgsConstructor
public class ActivityRegistrationCountService {

    private static final Set<RegistrationStatus> COUNTED_STATUSES = Set.of(
            RegistrationStatus.registered,
            RegistrationStatus.checkedIn,
            RegistrationStatus.waiting,
            RegistrationStatus.waitingConfirmation);

    private final ActivityRegistrationRepository activityRegistrationRepository;

    @Transactional(readOnly = true)
    public Map<String, ActivityRegistrationCounts> countByActivityIds(Collection<String> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Map.of();
        }

        Map<String, MutableCounts> countsByActivityId = new HashMap<>();
        List<ActivityRegistrationRepository.ActivityRegistrationStatusCount> statusCounts =
                activityRegistrationRepository.countByActivityIdsAndStatuses(activityIds, COUNTED_STATUSES);
        for (ActivityRegistrationRepository.ActivityRegistrationStatusCount statusCount : statusCounts) {
            countsByActivityId
                    .computeIfAbsent(statusCount.getActivityId(), ignored -> new MutableCounts())
                    .add(statusCount.getStatus(), statusCount.getTotal());
        }

        Map<String, ActivityRegistrationCounts> result = new HashMap<>();
        for (String activityId : activityIds) {
            MutableCounts mutableCounts = countsByActivityId.get(activityId);
            result.put(
                    activityId, mutableCounts == null ? ActivityRegistrationCounts.zero() : mutableCounts.toCounts());
        }
        return Map.copyOf(result);
    }

    @Transactional(readOnly = true)
    public ActivityRegistrationCounts countByActivityId(String activityId) {
        return countByActivityIds(List.of(activityId)).getOrDefault(activityId, ActivityRegistrationCounts.zero());
    }

    private static class MutableCounts {
        private int registeredCount;
        private int occupiedCount;
        private int waitingCount;

        void add(RegistrationStatus status, long total) {
            int count = Math.toIntExact(total);
            if (status == RegistrationStatus.registered || status == RegistrationStatus.checkedIn) {
                registeredCount += count;
                occupiedCount += count;
            } else if (status == RegistrationStatus.waitingConfirmation) {
                occupiedCount += count;
                waitingCount += count;
            } else if (status == RegistrationStatus.waiting) {
                waitingCount += count;
            }
        }

        ActivityRegistrationCounts toCounts() {
            return new ActivityRegistrationCounts(registeredCount, occupiedCount, waitingCount);
        }
    }
}

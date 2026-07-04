package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.service.TeamPointService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动运行时状态自动流转调度器。
 *
 * <p>类职责：定时检查已审核通过的活动，根据当前时间自动更新运行时状态。
 *
 * <p>自动流转规则（按优先级）：
 * <ol>
 *   <li>endAt 已过 → ended</li>
 *   <li>startAt 已到 → ongoing</li>
 *   <li>registrationDeadline 已过 → registrationClosed</li>
 *   <li>其它 → registering</li>
 * </ol>
 *
 * <p>类不变量：仅处理 reviewStatus=approved 且 runtimeStatus 不为 takenDown、ended 的活动。
 */
@Slf4j
@Component
public class ActivityRuntimeScheduler {

    private final ActivityRepository activityRepository;
    private final TeamPointService teamPointService;

    public ActivityRuntimeScheduler(ActivityRepository activityRepository, TeamPointService teamPointService) {
        this.activityRepository = activityRepository;
        this.teamPointService = teamPointService;
    }

    /**
     * 每分钟执行一次运行时状态自动流转。
     *
     * <p>前置条件：数据库中存在已审核通过的活动。
     *
     * <p>后置条件：符合条件的活动运行时状态根据时间更新。
     */
    @Scheduled(fixedRateString = "PT1M")
    @Transactional
    public void transitionRuntimeStatuses() {
        List<Activity> activities = activityRepository.findByReviewStatusAndRuntimeStatusNotIn(
                ActivityReviewStatus.approved, Set.of(ActivityRuntimeStatus.takenDown, ActivityRuntimeStatus.ended));

        Instant now = Instant.now();
        int updatedCount = 0;

        for (Activity activity : activities) {
            ActivityRuntimeStatus newStatus = computeTargetStatus(activity, now);
            if (activity.getRuntimeStatus() != newStatus) {
                ActivityRuntimeStatus oldStatus = activity.getRuntimeStatus();
                activity.setRuntimeStatus(newStatus);
                activity.setUpdatedAt(now);
                activityRepository.save(activity);
                updatedCount++;
                log.debug(
                        "活动运行时状态自动流转，activityId={}, {} → {}",
                        activity.getActivityId(),
                        oldStatus,
                        newStatus);

                if (newStatus == ActivityRuntimeStatus.ended) {
                    try {
                        teamPointService.processNoShows(activity.getActivityId());
                    } catch (Exception e) {
                        log.warn("爽约扣分处理失败: activityId={}", activity.getActivityId(), e);
                    }
                }
            }
        }

        if (updatedCount > 0) {
            log.info("本次运行时状态自动流转更新了 {} 个活动", updatedCount);
        }
    }

    /**
     * 根据活动时间和当前时间计算目标运行时状态。
     *
     * <p>前置条件：activity 非空且包含有效的时间字段。
     *
     * <p>后置条件：返回应该在当前时刻展示的运行时状态。
     *
     * @param activity 活动实体
     * @param now 当前时间
     * @return 目标运行时状态
     */
    public ActivityRuntimeStatus computeTargetStatus(Activity activity, Instant now) {
        if (activity.getEndAt() != null && !now.isBefore(activity.getEndAt())) {
            return ActivityRuntimeStatus.ended;
        }
        if (activity.getStartAt() != null && !now.isBefore(activity.getStartAt())) {
            return ActivityRuntimeStatus.ongoing;
        }
        if (activity.getRegistrationDeadline() != null && !now.isBefore(activity.getRegistrationDeadline())) {
            return ActivityRuntimeStatus.registrationClosed;
        }
        return ActivityRuntimeStatus.registering;
    }
}

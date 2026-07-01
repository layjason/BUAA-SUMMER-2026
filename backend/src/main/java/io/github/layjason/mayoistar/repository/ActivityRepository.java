package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, String> {

    Page<Activity> findByOrganizerIdAndReviewStatusOrderByUpdatedAtDesc(
            String organizerId, ActivityReviewStatus reviewStatus, Pageable pageable);

    Page<Activity> findByOrganizerIdAndReviewStatusInOrderByUpdatedAtDesc(
            String organizerId, Collection<ActivityReviewStatus> reviewStatuses, Pageable pageable);

    Page<Activity> findByOrganizerIdOrderByUpdatedAtDesc(String organizerId, Pageable pageable);

    /**
     * 查询地图上可展示的活动：不是草稿、未被下架、有坐标信息。
     */
    List<Activity> findByReviewStatusNotAndRuntimeStatusNotAndPointLatIsNotNullAndPointLonIsNotNull(
            ActivityReviewStatus excludedReviewStatus, ActivityRuntimeStatus excludedRuntimeStatus);

    /**
     * 查询需要自动流转运行时状态的活动：已审核通过，且不在排除的运行状态中。
     */
    List<Activity> findByReviewStatusAndRuntimeStatusNotIn(
            ActivityReviewStatus reviewStatus, Collection<ActivityRuntimeStatus> excludedRuntimeStatuses);
}

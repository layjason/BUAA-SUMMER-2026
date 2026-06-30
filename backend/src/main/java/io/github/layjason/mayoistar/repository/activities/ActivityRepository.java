package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 活动数据访问层。
 *
 * <p>类职责：提供 Activity 实体的基础 CRUD、组织者活动查询与动态筛选查询能力。
 *
 * <p>类不变量：Repository 不承载业务规则，只表达持久化访问边界。
 */
public interface ActivityRepository extends JpaRepository<Activity, String>, JpaSpecificationExecutor<Activity> {

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

package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 活动数据访问层。
 *
 * <p>类职责：提供 Activity 实体的 CRUD、按组织者查询及复杂筛选分页查询。
 */
public interface ActivityRepository extends JpaRepository<Activity, String>, JpaSpecificationExecutor<Activity> {

    /**
     * 统计指定用户发布的活动数量。
     *
     * @param organizerId 组织者用户 ID
     * @return 活动数量
     */
    long countByOrganizerId(String organizerId);

    /**
     * 按组织者分页查询活动。
     *
     * @param organizerId 组织者用户 ID
     * @param pageable    分页参数
     * @return 分页结果
     */
    Page<Activity> findByOrganizerId(String organizerId, Pageable pageable);

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

    /**
     * 查询指定小队下的活动。
     *
     * @param teamId   小队 ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Activity> findByTeamId(String teamId, Pageable pageable);

    /**
     * 查询用户创建或参与（包括小队）的活动。
     *
     * @param userId   用户 ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query("SELECT DISTINCT a FROM Activity a "
            + "LEFT JOIN TeamMember tm ON a.teamId = tm.teamId AND tm.userId = :userId "
            + "WHERE a.organizerId = :userId OR tm.userId = :userId")
    Page<Activity> findByUserIdOrTeamMember(@Param("userId") String userId, Pageable pageable);

    /**
     * 查询用户涉及的小队 ID 列表（创建者、队长或成员身份）。
     *
     * @param userId 用户 ID
     * @return 小队 ID 列表
     */
    @Query("SELECT DISTINCT t.teamId FROM Team t "
            + "LEFT JOIN TeamMember tm ON t.teamId = tm.teamId AND tm.userId = :userId "
            + "WHERE t.creatorId = :userId OR t.leaderId = :userId OR tm.userId = :userId")
    List<String> findTeamIdsByUserIdInvolvement(@Param("userId") String userId);
}

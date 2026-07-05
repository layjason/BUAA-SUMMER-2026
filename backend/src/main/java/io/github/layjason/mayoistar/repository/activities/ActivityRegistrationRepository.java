package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 活动报名记录数据访问层。
 */
public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, String> {

    interface ActivityRegistrationStatusCount {
        String getActivityId();

        RegistrationStatus getStatus();

        long getTotal();
    }

    Optional<ActivityRegistration> findByActivityIdAndUserId(String activityId, String userId);

    @Query("""
            select registration.activityId as activityId, registration.status as status, count(registration) as total
            from ActivityRegistration registration
            where registration.activityId in :activityIds and registration.status in :statuses
            group by registration.activityId, registration.status
            """)
    List<ActivityRegistrationStatusCount> countByActivityIdsAndStatuses(
            @Param("activityIds") Collection<String> activityIds,
            @Param("statuses") Collection<RegistrationStatus> statuses);

    @EntityGraph(attributePaths = "activity")
    Page<ActivityRegistration> findByUserIdOrderByRegisteredAtDesc(String userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<ActivityRegistration> findByActivityIdOrderByRegisteredAtDesc(String activityId, Pageable pageable);

    boolean existsByActivityIdAndUserIdAndStatusIn(
            String activityId, String userId, Collection<RegistrationStatus> statuses);

    long countByActivityIdAndStatusIn(String activityId, Collection<RegistrationStatus> statuses);

    List<ActivityRegistration> findByActivityIdAndStatusIn(String activityId, Collection<RegistrationStatus> statuses);

    Optional<ActivityRegistration> findFirstByActivityIdAndStatusOrderByWaitingRankAscRegisteredAtAsc(
            String activityId, RegistrationStatus status);

    List<ActivityRegistration> findByActivityIdAndStatusOrderByWaitingRankAscRegisteredAtAsc(
            String activityId, RegistrationStatus status);

    /**
     * 分页查询活动的所有报名记录。
     *
     * @param activityId 活动 ID
     * @param pageable   分页参数
     * @return 分页报名记录
     */
    Page<ActivityRegistration> findByActivityId(String activityId, Pageable pageable);

    /**
     * 按签到时间升序查询活动的所有报名记录，用于导出。
     *
     * @param activityId 活动 ID
     * @return 报名记录列表
     */
    List<ActivityRegistration> findByActivityIdOrderByCheckedInAtAsc(String activityId);

    /**
     * 按活动 ID 和用户 ID 加悲观写锁读取报名记录，用于签到流程防止重复签到。
     *
     * @param activityId 活动 ID
     * @param userId     用户 ID
     * @return 报名记录
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ar from ActivityRegistration ar where ar.activityId = :activityId and ar.userId = :userId")
    Optional<ActivityRegistration> findByActivityIdAndUserIdForUpdate(
            @Param("activityId") String activityId, @Param("userId") String userId);

    /**
     * 查询所有已过确认截止时间的候补待确认报名记录。
     *
     * @param status                候补待确认状态
     * @param confirmationDeadline  当前时间，用于比较截止时间
     * @return 过期候补待确认报名列表
     */
    List<ActivityRegistration> findByStatusAndConfirmationDeadlineBefore(
            RegistrationStatus status, Instant confirmationDeadline);
}

package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 活动报名记录数据访问层。
 */
public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, String> {

    Optional<ActivityRegistration> findByActivityIdAndUserId(String activityId, String userId);

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
}

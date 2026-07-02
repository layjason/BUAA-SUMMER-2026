package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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

    long countByActivityIdAndStatusIn(String activityId, Collection<RegistrationStatus> statuses);

    List<ActivityRegistration> findByActivityIdAndStatusIn(String activityId, Collection<RegistrationStatus> statuses);

    Optional<ActivityRegistration> findFirstByActivityIdAndStatusOrderByWaitingRankAscRegisteredAtAsc(
            String activityId, RegistrationStatus status);

    List<ActivityRegistration> findByActivityIdAndStatusOrderByWaitingRankAscRegisteredAtAsc(
            String activityId, RegistrationStatus status);
}

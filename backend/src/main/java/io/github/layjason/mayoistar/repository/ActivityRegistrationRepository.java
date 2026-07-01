package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 活动报名记录数据访问层。
 */
public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, String> {

    Optional<ActivityRegistration> findByActivityIdAndUserId(String activityId, String userId);
}

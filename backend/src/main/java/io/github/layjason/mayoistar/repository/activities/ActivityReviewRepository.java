package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivityReview;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 活动评价数据访问层。
 */
public interface ActivityReviewRepository extends JpaRepository<ActivityReview, String> {

    boolean existsByActivityIdAndUserId(String activityId, String userId);

    Page<ActivityReview> findByActivityId(String activityId, Pageable pageable);

    Optional<ActivityReview> findByActivityIdAndUserId(String activityId, String userId);
}

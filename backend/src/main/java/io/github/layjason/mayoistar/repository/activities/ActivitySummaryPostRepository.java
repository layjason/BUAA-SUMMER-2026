package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivitySummaryPost;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 活动图文总结数据访问层。
 */
public interface ActivitySummaryPostRepository extends JpaRepository<ActivitySummaryPost, String> {

    boolean existsByActivityId(String activityId);

    Page<ActivitySummaryPost> findByActivityId(String activityId, Pageable pageable);

    Optional<ActivitySummaryPost> findByActivityIdAndUserId(String activityId, String userId);
}

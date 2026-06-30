package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityReviewRecordRepository extends JpaRepository<ActivityReviewRecord, String> {

    List<ActivityReviewRecord> findByActivityIdOrderByReviewedAtDesc(String activityId);
}

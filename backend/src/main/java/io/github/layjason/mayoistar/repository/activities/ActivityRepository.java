package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, String> {

    Page<Activity> findByOrganizerIdAndReviewStatusOrderByUpdatedAtDesc(
            String organizerId, ActivityReviewStatus reviewStatus, Pageable pageable);

    Page<Activity> findByOrganizerIdOrderByUpdatedAtDesc(String organizerId, Pageable pageable);
}

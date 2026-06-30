package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivityImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityImageRepository extends JpaRepository<ActivityImage, String> {

    List<ActivityImage> findByActivityIdOrderBySortOrderAsc(String activityId);

    void deleteByActivityId(String activityId);
}

package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivitySummaryImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 活动总结图片数据访问层。
 */
public interface ActivitySummaryImageRepository extends JpaRepository<ActivitySummaryImage, String> {

    List<ActivitySummaryImage> findBySummaryIdIn(Collection<String> summaryIds);
}

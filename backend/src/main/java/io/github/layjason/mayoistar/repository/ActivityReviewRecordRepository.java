package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 活动审核记录数据访问层。
 *
 * <p>类职责：提供 ActivityReviewRecord 实体的 CRUD 及按活动查询。
 */
public interface ActivityReviewRecordRepository extends JpaRepository<ActivityReviewRecord, String> {

    /**
     * 查询指定活动的所有审核记录，按审核时间升序排列。
     *
     * @param activityId 活动 ID
     * @return 审核记录列表
     */
    List<ActivityReviewRecord> findByActivityIdOrderByReviewedAtAsc(String activityId);

    /**
     * 查询指定活动的所有审核记录，按审核时间降序排列。
     *
     * @param activityId 活动 ID
     * @return 审核记录列表
     */
    List<ActivityReviewRecord> findByActivityIdOrderByReviewedAtDesc(String activityId);
}

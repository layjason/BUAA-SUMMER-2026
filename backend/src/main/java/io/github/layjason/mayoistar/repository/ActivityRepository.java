package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.activities.Activity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 活动数据访问层。
 */
public interface ActivityRepository extends JpaRepository<Activity, String>, JpaSpecificationExecutor<Activity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activity a where a.activityId = :activityId")
    Optional<Activity> findByIdForUpdate(@Param("activityId") String activityId);
}

package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.activities.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 活动数据访问层。
 */
public interface ActivityRepository extends JpaRepository<Activity, String>, JpaSpecificationExecutor<Activity> {}

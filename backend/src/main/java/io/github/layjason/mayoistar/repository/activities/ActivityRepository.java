package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 活动数据访问层。
 *
 * <p>类职责：提供 Activity 实体的基础 CRUD 与动态筛选查询能力。
 *
 * <p>类不变量：Repository 不承载业务规则，只表达持久化访问边界。
 */
public interface ActivityRepository extends JpaRepository<Activity, String>, JpaSpecificationExecutor<Activity> {}

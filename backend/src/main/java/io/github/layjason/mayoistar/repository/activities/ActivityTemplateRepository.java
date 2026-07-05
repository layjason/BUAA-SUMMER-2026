package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivityTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 活动模板数据访问层。
 *
 * <p>类职责：提供活动模板的分页查询和按 ID 查询能力。
 */
public interface ActivityTemplateRepository extends JpaRepository<ActivityTemplate, String> {}

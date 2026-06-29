package io.github.layjason.mayoistar.entity.activities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 活动模板，预设活动信息供用户快速创建活动草稿。
 */
@Entity
@Table(name = "activity_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityTemplate {

    @Id
    @Column(name = "template_id", length = 36)
    private String templateId;

    @Column(nullable = false)
    private String name;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @Column(name = "default_tags", columnDefinition = "text")
    private String defaultTags;

    @Column(name = "default_introduction", columnDefinition = "text")
    private String defaultIntroduction;

    @Column(name = "default_safety_notice", columnDefinition = "text")
    private String defaultSafetyNotice;

    @Column(name = "default_capacity", nullable = false)
    private Integer defaultCapacity;

    @Column(name = "default_cover_image_media_id", length = 36)
    private String defaultCoverImageMediaId;
}

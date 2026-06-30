package io.github.layjason.mayoistar.entity.activities;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 活动模板，预设活动信息供用户快速创建活动草稿。
 */
@Entity
@Table(name = "activity_templates")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityTemplate {

    @Id
    @Column(name = "template_id", length = 36)
    private String templateId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_tags", columnDefinition = "jsonb")
    private List<String> defaultTags;

    @Column(name = "default_introduction", columnDefinition = "text")
    private String defaultIntroduction;

    @Column(name = "default_safety_notice", columnDefinition = "text")
    private String defaultSafetyNotice;

    @Column(name = "default_capacity", nullable = false)
    private Integer defaultCapacity;

    @Column(name = "default_cover_image_media_id", length = 36)
    private String defaultCoverImageMediaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_cover_image_media_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MediaFile defaultCoverImage;
}

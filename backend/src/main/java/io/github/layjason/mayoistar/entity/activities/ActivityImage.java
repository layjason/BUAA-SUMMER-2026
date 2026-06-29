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
 * 活动与媒体文件的关联，按排序展示。
 */
@Entity
@Table(name = "activity_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityImage {

    @Id
    @Column(name = "image_id", length = 36)
    private String imageId;

    @Column(name = "activity_id", length = 36, nullable = false)
    private String activityId;

    @Column(name = "media_id", length = 36, nullable = false)
    private String mediaId;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}

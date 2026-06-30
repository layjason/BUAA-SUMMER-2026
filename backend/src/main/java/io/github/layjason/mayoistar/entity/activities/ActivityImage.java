package io.github.layjason.mayoistar.entity.activities;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 活动与媒体文件的关联，按排序展示。
 */
@Entity
@Table(name = "activity_images")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityImage {

    @Id
    @Column(name = "image_id", length = 36)
    private String imageId;

    @Column(name = "activity_id", length = 36, nullable = false)
    private String activityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Activity activity;

    @Column(name = "media_id", length = 36, nullable = false)
    private String mediaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MediaFile media;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}

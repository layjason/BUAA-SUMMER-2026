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
 * 活动总结中的图片及其经人工确认的标签。
 */
@Entity
@Table(name = "activity_summary_images")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitySummaryImage {

    @Id
    @Column(name = "image_id", length = 36)
    private String imageId;

    @Column(name = "summary_id", length = 36, nullable = false)
    private String summaryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summary_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ActivitySummaryPost summary;

    @Column(name = "media_id", length = 36, nullable = false)
    private String mediaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MediaFile media;

    @Column(columnDefinition = "text")
    private String tags;
}

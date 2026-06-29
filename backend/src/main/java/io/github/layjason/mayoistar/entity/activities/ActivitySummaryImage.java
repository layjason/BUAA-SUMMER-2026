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
 * 活动总结中的图片及其经人工确认的标签。
 */
@Entity
@Table(name = "activity_summary_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitySummaryImage {

    @Id
    @Column(name = "image_id", length = 36)
    private String imageId;

    @Column(name = "summary_id", length = 36, nullable = false)
    private String summaryId;

    @Column(name = "media_id", length = 36, nullable = false)
    private String mediaId;

    @Column(columnDefinition = "text")
    private String tags;
}

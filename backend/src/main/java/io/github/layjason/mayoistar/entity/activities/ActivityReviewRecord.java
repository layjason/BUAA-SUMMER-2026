package io.github.layjason.mayoistar.entity.activities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 活动审核记录，保存每次审核的结果和原因。
 *
 * <p>AI 自动审核时 reviewer_id 为空，人工审核时记录审核人 ID。
 */
@Entity
@Table(name = "activity_review_records")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityReviewRecord {

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "activity_id", length = 36, nullable = false)
    private String activityId;

    @Column(nullable = false, length = 30)
    private String result;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "reviewer_id", length = 36)
    private String reviewerId;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;
}

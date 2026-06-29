package io.github.layjason.mayoistar.entity.activities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 活动评价，参与者对已结束活动的评分和文字评价。
 *
 * <p>一个用户对同一活动只能评价一次。
 */
@Entity
@Table(name = "activity_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityReview {

    @Id
    @Column(name = "review_id", length = 36)
    private String reviewId;

    @Column(name = "activity_id", length = 36, nullable = false)
    private String activityId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String tags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

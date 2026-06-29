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
 * 活动图文总结，活动结束后由发起人发布。
 */
@Entity
@Table(name = "activity_summary_posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitySummaryPost {

    @Id
    @Column(name = "summary_id", length = 36)
    private String summaryId;

    @Column(name = "activity_id", length = 36, nullable = false)
    private String activityId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

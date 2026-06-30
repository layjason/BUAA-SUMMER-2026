package io.github.layjason.mayoistar.entity.activities;

import io.github.layjason.mayoistar.entity.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * 活动图文总结，活动结束后由发起人发布。
 */
@Entity
@Table(name = "activity_summary_posts")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitySummaryPost {

    @Id
    @Column(name = "summary_id", length = 36)
    private String summaryId;

    @Column(name = "activity_id", length = 36, nullable = false)
    private String activityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Activity activity;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

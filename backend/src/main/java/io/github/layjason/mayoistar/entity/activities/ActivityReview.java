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
 * 活动评价，参与者对已结束活动的评分和文字评价。
 *
 * <p>一个用户对同一活动只能评价一次。
 */
@Entity
@Table(name = "activity_reviews")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityReview {

    @Id
    @Column(name = "review_id", length = 36)
    private String reviewId;

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

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

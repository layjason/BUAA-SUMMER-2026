package io.github.layjason.mayoistar.entity.activities;

import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.social.Team;
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
 * 活动主体，保存活动的完整信息。
 *
 * <p>地点信息以展平字段存储。review_status 和 runtime_status 分别控制审核流程和展示状态。
 */
@Entity
@Table(name = "activities")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Activity {

    @Id
    @Column(name = "activity_id", length = 36)
    private String activityId;

    @Column(name = "organizer_id", length = 36, nullable = false)
    private String organizerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User organizer;

    @Column(name = "team_id", length = 36)
    private String teamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Team team;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String tags;

    @Column(columnDefinition = "text")
    private String introduction;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "point_lon")
    private Double pointLon;

    @Column(name = "point_lat")
    private Double pointLat;

    private String city;

    private String address;

    @Column(name = "place_name")
    private String placeName;

    @Column(name = "safety_notice", columnDefinition = "text")
    private String safetyNotice;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "fee_amount")
    private Double feeAmount;

    @Column(name = "fee_description")
    private String feeDescription;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "registration_deadline")
    private Instant registrationDeadline;

    @Column(name = "review_status", nullable = false, length = 30)
    private String reviewStatus;

    @Column(name = "runtime_status", nullable = false, length = 30)
    private String runtimeStatus;

    @Column(name = "manual_review_required", nullable = false)
    @Builder.Default
    private Boolean manualReviewRequired = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

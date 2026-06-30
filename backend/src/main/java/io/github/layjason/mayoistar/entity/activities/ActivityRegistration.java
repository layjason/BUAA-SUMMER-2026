package io.github.layjason.mayoistar.entity.activities;

import io.github.layjason.mayoistar.entity.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 活动报名记录，跟踪用户的报名、候补、签到状态。
 *
 * <p>一个用户对同一个活动最多只有一条有效报名记录。
 */
@Entity
@Table(name = "activity_registrations")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRegistration {

    @Id
    @Column(name = "registration_id", length = 36)
    private String registrationId;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RegistrationStatus status;

    @Column(name = "participant_note")
    private String participantNote;

    @Column(name = "accepted_safety_notice", nullable = false)
    private Boolean acceptedSafetyNotice;

    @Column(name = "waiting_rank")
    private Integer waitingRank;

    @Column(name = "confirmation_deadline")
    private Instant confirmationDeadline;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;
}

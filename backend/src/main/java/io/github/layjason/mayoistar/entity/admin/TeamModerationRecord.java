package io.github.layjason.mayoistar.entity.admin;

import io.github.layjason.mayoistar.entity.social.Team;
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
 * 小队治理记录，记录后台对小队执行的停用和恢复操作。
 *
 * <p>类职责：持久化小队治理动作、原因、操作人和发生时间。
 *
 * <p>类不变量：teamId、action、reason、operatorId、createdAt 均非空；action 仅允许小队治理动作。
 */
@Entity
@Table(name = "team_moderation_records")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamModerationRecord {

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "team_id", length = 36, nullable = false)
    private String teamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminModerationAction action;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "operator_id", length = 36, nullable = false)
    private String operatorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Admin operator;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

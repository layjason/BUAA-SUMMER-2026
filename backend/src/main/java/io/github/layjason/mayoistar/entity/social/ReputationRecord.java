package io.github.layjason.mayoistar.entity.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 信誉积分变更记录，记录每次积分变动的原因和关联信息。
 *
 * <p>类职责：持久化信誉积分变更，支持审计追溯。
 * 仅保存变更记录和累加积分，不实现计算策略。
 *
 * <p>类不变量：recordId、userId、scoreChange、reason、source、createdAt 均非空。
 */
@Entity
@Table(name = "reputation_records")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReputationRecord {

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "score_change", nullable = false)
    private Integer scoreChange;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReputationChangeSource source;

    @Column(name = "reference_id", length = 36)
    private String referenceId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

package io.github.layjason.mayoistar.entity.social;

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
 * 小队积分变动记录，记录成员积分增减的历史明细。
 *
 * <p>用于积分榜的计算和审计追溯。
 */
@Entity
@Table(name = "team_point_records")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamPointRecord {

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "team_id", length = 36, nullable = false)
    private String teamId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "point_change", nullable = false)
    private Integer pointChange;

    @Column(nullable = false, length = 50)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

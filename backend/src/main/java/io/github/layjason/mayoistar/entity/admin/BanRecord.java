package io.github.layjason.mayoistar.entity.admin;

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
 * 封禁记录，记录用户被封禁和解封的历史。
 *
 * <p>每次封禁产生一条记录，解封时更新 unbanned_at 字段。
 */
@Entity
@Table(name = "ban_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BanRecord {

    @Id
    @Column(name = "ban_id", length = 36)
    private String banId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "operator_id", length = 36, nullable = false)
    private String operatorId;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "banned_at", nullable = false)
    private Instant bannedAt;

    @Column(name = "banned_until", nullable = false)
    private Instant bannedUntil;

    @Column(name = "unbanned_at")
    private Instant unbannedAt;
}

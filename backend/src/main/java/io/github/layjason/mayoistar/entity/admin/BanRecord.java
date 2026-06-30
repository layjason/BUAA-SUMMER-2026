package io.github.layjason.mayoistar.entity.admin;

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
 * 封禁记录，记录用户被封禁和解封的历史。
 *
 * <p>每次封禁产生一条记录，解封时更新 unbanned_at 字段。
 */
@Entity
@Table(name = "ban_records")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BanRecord {

    @Id
    @Column(name = "ban_id", length = 36)
    private String banId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(name = "operator_id", length = 36, nullable = false)
    private String operatorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Admin operator;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "banned_at", nullable = false)
    @Builder.Default
    private Instant bannedAt = Instant.now();

    @Column(name = "banned_until", nullable = false)
    @Builder.Default
    private Instant bannedUntil = Instant.now();

    @Column(name = "unbanned_at")
    private Instant unbannedAt;
}

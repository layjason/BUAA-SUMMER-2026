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
 * 黑名单，阻止关注、好友申请和小队加入等社交动作。
 */
@Entity
@Table(name = "blacklists")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Blacklist {

    @Id
    @Column(name = "blacklist_id", length = 36)
    private String blacklistId;

    @Column(name = "blocker_id", length = 36, nullable = false)
    private String blockerId;

    @Column(name = "blocked_user_id", length = 36, nullable = false)
    private String blockedUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

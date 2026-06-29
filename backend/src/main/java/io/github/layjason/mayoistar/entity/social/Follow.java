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
 * 关注关系，记录一方单向关注另一方的行为。
 *
 * <p>互相关注时可自动升级为好友关系。
 */
@Entity
@Table(name = "follows")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follow {

    @Id
    @Column(name = "follow_id", length = 36)
    private String followId;

    @Column(name = "follower_id", length = 36, nullable = false)
    private String followerId;

    @Column(name = "followed_id", length = 36, nullable = false)
    private String followedId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

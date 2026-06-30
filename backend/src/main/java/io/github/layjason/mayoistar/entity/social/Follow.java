package io.github.layjason.mayoistar.entity.social;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User follower;

    @Column(name = "followed_id", length = 36, nullable = false)
    private String followedId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followed_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User followed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

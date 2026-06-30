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
 * 好友关系，一行记录包含双向数据。
 *
 * <p>user_id_a 和 user_id_b 确定好友双方，a_* 和 b_* 分别存储双方各自的备注和分组标签。
 * 双方可各自独立设置备注与分组，互不影响。
 */
@Entity
@Table(name = "friendships")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship {

    @Id
    @Column(name = "friendship_id", length = 36)
    private String friendshipId;

    @Column(name = "user_id_a", length = 36, nullable = false)
    private String userIdA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id_a", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User userA;

    @Column(name = "user_id_b", length = 36, nullable = false)
    private String userIdB;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id_b", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User userB;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "a_remark")
    private String aRemark;

    @Column(name = "a_group_tags", columnDefinition = "text")
    private String aGroupTags;

    @Column(name = "b_remark")
    private String bRemark;

    @Column(name = "b_group_tags", columnDefinition = "text")
    private String bGroupTags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

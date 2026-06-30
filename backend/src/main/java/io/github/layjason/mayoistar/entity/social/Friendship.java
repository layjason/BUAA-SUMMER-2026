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
 * 好友关系，每人一条记录，存储"我"对"好友"的关系数据。
 *
 * <p>每对好友关系在表中存在两行（A→B 和 B→A），各自独立存储备注和分组标签。
 *
 * <p>前置条件（创建本实体前）：{@code userId} 与 {@code friendUserId} 为不同的有效用户，
 * 已通过好友申请或互关确认好友关系。
 *
 * <p>后置条件（创建本实体后）：一条 Friendships 记录已持久化。
 *
 * <p>不变量：{@code userId != friendUserId}，且 {@code (user_id, friend_user_id)}
 * 组合唯一，同一人不会对同一好友存在多条记录。
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

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "friend_user_id", length = 36, nullable = false)
    private String friendUserId;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(length = 50)
    private String remark;

    @Column(name = "group_tags", columnDefinition = "text")
    private String groupTags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

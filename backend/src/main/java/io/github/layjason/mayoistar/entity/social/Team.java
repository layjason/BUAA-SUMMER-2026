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
 * 小队，兴趣社交的基本组织单位。
 *
 * <p>创建小队时自动生成群聊会话（chat_id）。小队解散或停用后不再出现在发现列表。
 */
@Entity
@Table(name = "teams")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @Column(name = "team_id", length = 36)
    private String teamId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String tags;

    @Column(name = "join_mode", nullable = false, length = 30)
    private String joinMode;

    @Column(nullable = false)
    private Integer capacity;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "avatar_media_id", length = 36)
    private String avatarMediaId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "leader_id", length = 36, nullable = false)
    private String leaderId;

    @Column(name = "chat_id", length = 36)
    private String chatId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

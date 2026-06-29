package io.github.layjason.mayoistar.entity.chat;

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
 * 会话，表示一个好友对话或小队群聊。
 *
 * <p>kind 区分好友会话和小队群聊。小队群聊的成员与小队的成员同步。
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @Column(name = "conversation_id", length = 36)
    private String conversationId;

    @Column(nullable = false, length = 20)
    private String kind;

    private String title;

    @Column(name = "avatar_media_id", length = 36)
    private String avatarMediaId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

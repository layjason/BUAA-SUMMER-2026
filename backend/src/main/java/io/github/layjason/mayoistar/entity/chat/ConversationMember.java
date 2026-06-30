package io.github.layjason.mayoistar.entity.chat;

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
 * 会话成员，记录用户属于哪些会话。
 */
@Entity
@Table(name = "conversation_members")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMember {

    @Id
    @Column(name = "member_id", length = 36)
    private String memberId;

    @Column(name = "conversation_id", length = 36, nullable = false)
    private String conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversation conversation;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();
}

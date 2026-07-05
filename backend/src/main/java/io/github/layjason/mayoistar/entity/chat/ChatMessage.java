package io.github.layjason.mayoistar.entity.chat;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 聊天消息，支持文本、图片和位置共享三种类型。
 *
 * <p>撤回后 recalled 为 true，消息内容保持不动用于审计。mention_all 仅队长和管理员可用。
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @Column(name = "message_id", length = 36)
    private String messageId;

    @Column(name = "conversation_id", length = 36, nullable = false)
    private String conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversation conversation;

    @Column(name = "sender_id", length = 36, nullable = false)
    private String senderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageKind kind;

    @Column(columnDefinition = "text")
    private String text;

    @Column(name = "image_media_id", columnDefinition = "UUID")
    private UUID imageMediaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_media_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MediaFile image;

    @Column(name = "location_lon")
    private Double locationLon;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_city", length = 100)
    private String locationCity;

    @Column(name = "location_address", length = 500)
    private String locationAddress;

    @Column(name = "location_place_name", length = 200)
    private String locationPlaceName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mentioned_user_ids", columnDefinition = "jsonb")
    private List<String> mentionedUserIds;

    @Column(name = "mention_all")
    private Boolean mentionAll;

    @Column(nullable = false)
    @Builder.Default
    private Boolean recalled = false;

    @Column(name = "sent_at", nullable = false)
    @Builder.Default
    private Instant sentAt = Instant.now();
}

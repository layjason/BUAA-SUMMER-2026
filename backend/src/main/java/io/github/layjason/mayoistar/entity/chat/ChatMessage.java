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

    @Column(name = "sender_id", length = 36, nullable = false)
    private String senderId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(columnDefinition = "text")
    private String text;

    @Column(name = "image_media_id", length = 36)
    private String imageMediaId;

    @Column(name = "location_lon")
    private Double locationLon;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_city")
    private String locationCity;

    @Column(name = "location_address")
    private String locationAddress;

    @Column(name = "location_place_name")
    private String locationPlaceName;

    @Column(name = "mentioned_user_ids", columnDefinition = "text")
    private String mentionedUserIds;

    @Column(name = "mention_all")
    private Boolean mentionAll;

    @Column(nullable = false)
    @Builder.Default
    private Boolean recalled = false;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}

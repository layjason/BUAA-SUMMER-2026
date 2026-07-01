package io.github.layjason.mayoistar.entity.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 消息读取状态，记录每用户对每条消息的已读/未读状态。
 *
 * <p>前置条件：{@code messageId} 对应的消息已创建，{@code userId} 为会话成员。
 *
 * <p>后置条件：一条 message_reads 记录已持久化。
 *
 * <p>不变量：{@code (message_id, user_id)} 组合唯一。
 */
@Entity
@Table(name = "message_reads")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRead {

    @Id
    @Column(name = "read_id", length = 36)
    private String readId;

    @Column(name = "message_id", length = 36, nullable = false)
    private String messageId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageReadStatus status;

    @Column(name = "read_at")
    private Instant readAt;
}

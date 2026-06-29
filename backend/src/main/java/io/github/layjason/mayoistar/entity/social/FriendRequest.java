package io.github.layjason.mayoistar.entity.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友申请，记录一方发起的好友申请及其处理状态。
 */
@Entity
@Table(name = "friend_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendRequest {

    @Id
    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "requester_id", length = 36, nullable = false)
    private String requesterId;

    @Column(name = "target_user_id", length = 36, nullable = false)
    private String targetUserId;

    @Column(nullable = false, length = 30)
    private String source;

    private String message;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

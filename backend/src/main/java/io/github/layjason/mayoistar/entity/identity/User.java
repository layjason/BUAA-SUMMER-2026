package io.github.layjason.mayoistar.entity.identity;

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
 * 用户账号，统一存储个人用户、商家和管理员三种类型。
 *
 * <p>kind 字段区分用户类型，email 与 username 互斥（仅管理员使用 username 登录，其余使用 email）。
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "banned_at")
    private Instant bannedAt;

    @Column(name = "banned_until")
    private Instant bannedUntil;

    @Column(name = "ban_reason")
    private String banReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

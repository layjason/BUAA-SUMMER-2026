package io.github.layjason.mayoistar.entity.identity;

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
 * 安全令牌，统一存储激活令牌、密码重置令牌和刷新令牌。
 *
 * <p>token_type 区分令牌用途，expires_at 控制有效期，used/revoked 控制是否已消费。
 */
@Entity
@Table(name = "security_tokens")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityToken {

    @Id
    @Column(name = "token_id", length = 36)
    private String tokenId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "token_type", nullable = false, length = 20)
    private String tokenType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private Boolean used;

    private Boolean revoked;
}

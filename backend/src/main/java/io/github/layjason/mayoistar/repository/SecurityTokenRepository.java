package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.identity.SecurityToken;
import io.github.layjason.mayoistar.entity.identity.TokenType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 安全令牌数据访问层。
 *
 * <p>类职责：提供 SecurityToken 实体的 CRUD 及按 tokenHash / userId + tokenType 查询、批量吊销。
 */
public interface SecurityTokenRepository extends JpaRepository<SecurityToken, String> {

    Optional<SecurityToken> findByTokenHash(String tokenHash);

    List<SecurityToken> findByUserIdAndTokenType(String userId, TokenType tokenType);

    /**
     * 按用户 ID 和令牌类型查询最近创建的令牌，用于频率限制。
     */
    Optional<SecurityToken> findFirstByUserIdAndTokenTypeOrderByCreatedAtDesc(String userId, TokenType tokenType);

    @Modifying
    @Query(
            "UPDATE SecurityToken t SET t.revoked = true WHERE t.userId = :userId AND t.tokenType = :tokenType AND t.revoked = false")
    void revokeAllByUserIdAndTokenType(String userId, TokenType tokenType);
}

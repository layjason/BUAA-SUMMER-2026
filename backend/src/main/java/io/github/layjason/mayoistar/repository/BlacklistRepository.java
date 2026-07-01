package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.Blacklist;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 黑名单数据访问层。
 *
 * <p>类职责：提供 Blacklist 实体的 CRUD 及按屏蔽者/被屏蔽用户查询。
 */
public interface BlacklistRepository extends JpaRepository<Blacklist, String> {

    boolean existsByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);

    Optional<Blacklist> findByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);

    Page<Blacklist> findByBlockerIdOrderByCreatedAtDesc(String blockerId, Pageable pageable);

    void deleteByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);
}

package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.Blacklist;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, String> {

    boolean existsByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);

    Optional<Blacklist> findByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);

    Page<Blacklist> findByBlockerIdOrderByCreatedAtDesc(String blockerId, Pageable pageable);

    void deleteByBlockerIdAndBlockedUserId(String blockerId, String blockedUserId);
}

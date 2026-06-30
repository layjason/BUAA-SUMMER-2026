package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.Friendship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 好友关系数据访问层。
 *
 * <p>类职责：提供 Friendship 实体的 CRUD 及按用户查询好友列表。
 */
public interface FriendshipRepository extends JpaRepository<Friendship, String> {

    boolean existsByUserIdAndFriendUserId(String userId, String friendUserId);

    Page<Friendship> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    void deleteByUserIdAndFriendUserId(String userId, String friendUserId);
}

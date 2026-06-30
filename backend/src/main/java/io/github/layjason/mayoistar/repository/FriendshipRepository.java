package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.Friendship;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 好友关系数据访问层。
 *
 * <p>类职责：提供 Friendship 实体的 CRUD 及按用户查询好友列表。
 */
public interface FriendshipRepository extends JpaRepository<Friendship, String> {

    boolean existsByUserIdAndFriendUserId(String userId, String friendUserId);

    Optional<Friendship> findByUserIdAndFriendUserId(String userId, String friendUserId);

    Page<Friendship> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    void deleteByUserIdAndFriendUserId(String userId, String friendUserId);

    @Modifying
    @Query(
            "DELETE FROM Friendship f WHERE (f.userId = :userIdA AND f.friendUserId = :userIdB) OR (f.userId = :userIdB AND f.friendUserId = :userIdA)")
    void deleteBilateral(String userIdA, String userIdB);

    @Query("SELECT f FROM Friendship f JOIN User u ON f.friendUserId = u.userId "
            + "WHERE f.userId = :userId AND LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "ORDER BY f.createdAt DESC")
    Page<Friendship> findByUserIdAndFriendNicknameContaining(
            @Param("userId") String userId, @Param("keyword") String keyword, Pageable pageable);
}

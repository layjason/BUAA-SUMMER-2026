package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.Friendship;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, String> {

    boolean existsByUserIdAndFriendUserId(String userId, String friendUserId);

    Optional<Friendship> findByUserIdAndFriendUserId(String userId, String friendUserId);

    Page<Friendship> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Modifying
    @Query(
            "DELETE FROM Friendship f WHERE (f.userId = :userIdA AND f.friendUserId = :userIdB) OR (f.userId = :userIdB AND f.friendUserId = :userIdA)")
    void deleteBilateral(String userIdA, String userIdB);
}

package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.FriendRequest;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, String> {

    boolean existsByRequesterIdAndTargetUserIdAndStatus(
            String requesterId, String targetUserId, FriendRequestStatus status);

    Page<FriendRequest> findByRequesterIdOrderByCreatedAtDesc(String requesterId, Pageable pageable);

    Page<FriendRequest> findByTargetUserIdOrderByCreatedAtDesc(String targetUserId, Pageable pageable);

    Optional<FriendRequest> findByRequestIdAndTargetUserIdAndStatus(
            String requestId, String targetUserId, FriendRequestStatus status);
}

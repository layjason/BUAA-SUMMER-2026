package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.FriendRequest;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 好友申请数据访问层。
 *
 * <p>类职责：提供 FriendRequest 实体的 CRUD 及按发起者/目标用户/状态查询。
 */
public interface FriendRequestRepository extends JpaRepository<FriendRequest, String> {

    boolean existsByRequesterIdAndTargetUserIdAndStatus(
            String requesterId, String targetUserId, FriendRequestStatus status);

    Page<FriendRequest> findByTargetUserIdOrderByCreatedAtDesc(String targetUserId, Pageable pageable);

    Page<FriendRequest> findByTargetUserIdAndStatusOrderByCreatedAtDesc(
            String targetUserId, FriendRequestStatus status, Pageable pageable);

    Page<FriendRequest> findByRequesterIdOrderByCreatedAtDesc(String requesterId, Pageable pageable);

    Page<FriendRequest> findByRequesterIdAndStatusOrderByCreatedAtDesc(
            String requesterId, FriendRequestStatus status, Pageable pageable);

    Optional<FriendRequest> findByRequestIdAndTargetUserIdAndStatus(
            String requestId, String targetUserId, FriendRequestStatus status);
}

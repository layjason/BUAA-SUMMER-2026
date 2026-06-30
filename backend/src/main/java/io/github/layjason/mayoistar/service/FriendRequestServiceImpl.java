package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.social.FriendRequest;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.entity.social.FriendshipSource;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.FriendRequestRepository;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 好友申请服务实现。
 *
 * <p>类职责：实现好友申请的发送、处理、查询业务逻辑。
 *
 * <p>不变量：发送申请前校验目标用户存在性、非自引用、非黑名单、非好友、无重复申请。
 */
@Slf4j
@Service
public class FriendRequestServiceImpl implements FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final BlacklistRepository blacklistRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FriendRequestServiceImpl(
            FriendRequestRepository friendRequestRepository,
            FriendshipRepository friendshipRepository,
            BlacklistRepository blacklistRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.friendRequestRepository = friendRequestRepository;
        this.friendshipRepository = friendshipRepository;
        this.blacklistRepository = blacklistRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * 发送好友申请。
     *
     * <p>前置条件：目标用户存在，不是自己，未拉黑对方/未被对方拉黑，不是好友，
     * 且两方向均无待处理申请。
     *
     * <p>后置条件：一条 pending 状态的 FriendRequest 记录已持久化。
     *
     * @param requesterId 发起者用户 ID
     * @param targetUserId 目标用户 ID
     * @param source       申请来源
     * @param message      申请附言
     * @return 创建的好友申请 DTO
     */
    @Override
    @Transactional
    public SocialDtos.FriendRequest createFriendRequest(
            String requesterId, String targetUserId, FriendRequestSource source, String message) {
        if (requesterId.equals(targetUserId)) {
            throw new BusinessException(40000, "Cannot send friend request to yourself");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new BusinessException(40000, "User " + targetUserId + " is not visible");
        }
        if (blacklistRepository.existsByBlockerIdAndBlockedUserId(requesterId, targetUserId)
                || blacklistRepository.existsByBlockerIdAndBlockedUserId(targetUserId, requesterId)) {
            throw new BusinessException(40001, "Blacklist relation blocks this operation");
        }
        if (friendshipRepository.existsByUserIdAndFriendUserId(requesterId, targetUserId)) {
            throw new BusinessException(40004, "Friendship state does not allow this operation");
        }
        if (friendRequestRepository.existsByRequesterIdAndTargetUserIdAndStatus(
                requesterId, targetUserId, FriendRequestStatus.pending)) {
            throw new BusinessException(40006, "Friend request already exists");
        }
        if (friendRequestRepository.existsByRequesterIdAndTargetUserIdAndStatus(
                targetUserId, requesterId, FriendRequestStatus.pending)) {
            throw new BusinessException(40006, "Friend request already exists");
        }

        FriendRequest request = FriendRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .requesterId(requesterId)
                .targetUserId(targetUserId)
                .source(source)
                .message(message)
                .status(FriendRequestStatus.pending)
                .createdAt(Instant.now())
                .build();
        friendRequestRepository.save(request);

        log.info("好友申请发送成功: requester={}, target={}", requesterId, targetUserId);
        SocialDtos.FriendRequest result = toFriendRequestDto(request);
        notificationService.notifyFriendRequestCreated(result);
        return result;
    }

    /**
     * 处理好友申请（接受或拒绝）。
     *
     * <p>前置条件：requestId 对应 pending 状态的申请，且当前用户为目标用户。
     *
     * <p>后置条件：状态更新。若接受，创建双向 Friendship 记录。
     *
     * @param currentUserId 当前用户 ID
     * @param requestId     申请 ID
     * @param accepted      是否接受
     * @return 更新后的好友申请 DTO
     */
    @Override
    @Transactional
    public SocialDtos.FriendRequest decideFriendRequest(String currentUserId, String requestId, boolean accepted) {
        FriendRequest request = friendRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(40005, "Friend request state does not allow this operation"));
        if (request.getStatus() != FriendRequestStatus.pending) {
            throw new BusinessException(40005, "Friend request state does not allow this operation");
        }
        if (!request.getTargetUserId().equals(currentUserId)) {
            throw new BusinessException(40005, "Friend request state does not allow this operation");
        }

        if (accepted) {
            request.setStatus(FriendRequestStatus.accepted);
            friendRequestRepository.save(request);

            Instant now = Instant.now();
            Friendship fsAtoB = Friendship.builder()
                    .friendshipId(UUID.randomUUID().toString())
                    .userId(request.getRequesterId())
                    .friendUserId(request.getTargetUserId())
                    .source(FriendshipSource.manualRequest)
                    .createdAt(now)
                    .build();
            Friendship fsBtoA = Friendship.builder()
                    .friendshipId(UUID.randomUUID().toString())
                    .userId(request.getTargetUserId())
                    .friendUserId(request.getRequesterId())
                    .source(FriendshipSource.manualRequest)
                    .createdAt(now)
                    .build();
            friendshipRepository.save(fsAtoB);
            friendshipRepository.save(fsBtoA);

            log.info("好友申请通过，好友关系建立: {} <-> {}", request.getRequesterId(), request.getTargetUserId());
        } else {
            request.setStatus(FriendRequestStatus.rejected);
            friendRequestRepository.save(request);

            log.info("好友申请被拒绝: requester={}, target={}", request.getRequesterId(), request.getTargetUserId());
        }

        return toFriendRequestDto(request);
    }

    /**
     * 查询收到的好友申请。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页申请列表。
     */
    @Override
    public PageResult<SocialDtos.FriendRequest> listReceivedRequests(
            String userId, FriendRequestStatus status, int page, int pageSize) {
        var requestPage = (status != null)
                ? friendRequestRepository.findByTargetUserIdAndStatusOrderByCreatedAtDesc(
                        userId, status, PageRequest.of(page - 1, pageSize))
                : friendRequestRepository.findByTargetUserIdOrderByCreatedAtDesc(
                        userId, PageRequest.of(page - 1, pageSize));

        return toPageResult(requestPage, page, pageSize);
    }

    /**
     * 查询已发送的好友申请。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页申请列表。
     */
    @Override
    public PageResult<SocialDtos.FriendRequest> listSentRequests(
            String userId, FriendRequestStatus status, int page, int pageSize) {
        var requestPage = (status != null)
                ? friendRequestRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(
                        userId, status, PageRequest.of(page - 1, pageSize))
                : friendRequestRepository.findByRequesterIdOrderByCreatedAtDesc(
                        userId, PageRequest.of(page - 1, pageSize));

        return toPageResult(requestPage, page, pageSize);
    }

    private SocialDtos.FriendRequest toFriendRequestDto(FriendRequest request) {
        SocialDtos.FriendRequest dto = new SocialDtos.FriendRequest();
        dto.setRequestId(request.getRequestId());
        dto.setRequesterId(request.getRequesterId());
        dto.setTargetUserId(request.getTargetUserId());
        dto.setSource(request.getSource());
        dto.setMessage(request.getMessage());
        dto.setStatus(request.getStatus());
        dto.setCreatedAt(request.getCreatedAt().toString());
        return dto;
    }

    private PageResult<SocialDtos.FriendRequest> toPageResult(
            org.springframework.data.domain.Page<FriendRequest> page, int pageNum, int pageSize) {
        List<SocialDtos.FriendRequest> items =
                page.getContent().stream().map(this::toFriendRequestDto).toList();

        PageResult<SocialDtos.FriendRequest> result = new PageResult<>();
        result.setItems(items);
        result.setTotal(page.getTotalElements());
        result.setPage(pageNum);
        result.setPageSize(pageSize);
        result.setTotalPages(page.getTotalPages());
        return result;
    }
}

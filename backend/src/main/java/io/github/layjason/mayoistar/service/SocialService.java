package io.github.layjason.mayoistar.service;

import static io.github.layjason.mayoistar.exception.ErrorCodes.BLACKLIST_RELATION_EXISTS;
import static io.github.layjason.mayoistar.exception.ErrorCodes.DUPLICATE_FRIEND_REQUEST;
import static io.github.layjason.mayoistar.exception.ErrorCodes.FOLLOW_ALREADY_EXISTS;
import static io.github.layjason.mayoistar.exception.ErrorCodes.FOLLOW_NOT_FOUND;
import static io.github.layjason.mayoistar.exception.ErrorCodes.FRIENDSHIP_STATE_INVALID;
import static io.github.layjason.mayoistar.exception.ErrorCodes.FRIEND_REQUEST_STATE_INVALID;
import static io.github.layjason.mayoistar.exception.ErrorCodes.USER_NOT_VISIBLE;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.social.Follow;
import io.github.layjason.mayoistar.entity.social.FriendRequest;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.entity.social.FriendshipSource;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.FollowRepository;
import io.github.layjason.mayoistar.repository.FriendRequestRepository;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 好友社群服务，管理关注、好友关系、好友申请和黑名单。
 *
 * <p>类职责：封装所有社交关系的业务逻辑，包括互相关注自动升级好友、取消关注时区分手动/互关来源等核心规则。
 *
 * <p>类不变量：所有方法均在事务内执行，保证数据一致性。
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SocialService {

    private final UserRepository userRepository;
    private final PersonalProfileRepository personalProfileRepository;
    private final FollowRepository followRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final BlacklistRepository blacklistRepository;
    private final NotificationService notificationService;

    // ========================================
    // Personal Profile
    // ========================================

    /**
     * 获取用户个人主页。
     *
     * <p>前置条件：{@code currentUserId} 与 {@code targetUserId} 为有效用户。
     *
     * <p>后置条件：返回目标用户的公开资料，或抛出业务异常。
     *
     * @param currentUserId 当前登录用户 ID
     * @param targetUserId  目标用户 ID
     * @return 目标用户公开资料
     * @throws BusinessException 目标用户不存在或存在黑名单关系时抛出
     */
    @Transactional(readOnly = true)
    public IdentityDtos.PublicUserProfile getUserProfile(String currentUserId, String targetUserId) {
        User targetUser = userRepository.findById(targetUserId).orElseThrow(() -> {
            log.warn("目标用户不存在: targetUserId={}", targetUserId);
            return new BusinessException(USER_NOT_VISIBLE, "User " + targetUserId + " is not visible");
        });

        if (blacklistRepository.existsByBlockerIdAndBlockedUserId(targetUserId, currentUserId)) {
            log.warn("黑名单关系阻止查看: blocker={}, currentUser={}", targetUserId, currentUserId);
            throw new BusinessException(BLACKLIST_RELATION_EXISTS, "Blacklist relation blocks this operation");
        }

        PersonalProfile profile = personalProfileRepository
                .findByUserId(targetUserId)
                .orElse(PersonalProfile.builder().userId(targetUserId).build());

        return toPublicUserProfile(targetUser, profile);
    }

    // ========================================
    // Follow / Unfollow
    // ========================================

    /**
     * 关注用户。互相关注时自动升级为好友。
     *
     * <p>前置条件：{@code followerId} 与 {@code followedId} 为不同的有效用户，不存在黑名单关系。
     *
     * <p>后置条件：关注关系已创建，若互相关注则好友关系已创建。
     *
     * @param followerId 关注者 ID
     * @param followedId 被关注者 ID
     * @return 关注关系结果
     * @throws BusinessException 目标不存在、黑名单或已关注时抛出
     */
    public SocialDtos.FollowRelation followUser(String followerId, String followedId) {
        if (!userRepository.existsById(followedId)) {
            log.warn("目标用户不存在: followedId={}", followedId);
            throw new BusinessException(USER_NOT_VISIBLE, "User " + followedId + " is not visible");
        }

        if (blacklistRepository.existsByBlockerIdAndBlockedUserId(followedId, followerId)
                || blacklistRepository.existsByBlockerIdAndBlockedUserId(followerId, followedId)) {
            log.warn("黑名单关系阻止关注: follower={}, followed={}", followerId, followedId);
            throw new BusinessException(BLACKLIST_RELATION_EXISTS, "Blacklist relation blocks this operation");
        }

        if (followRepository.existsByFollowerIdAndFollowedId(followerId, followedId)) {
            log.warn("关注关系已存在: follower={}, followed={}", followerId, followedId);
            throw new BusinessException(FOLLOW_ALREADY_EXISTS, "Follow relation already exists");
        }

        Follow follow = Follow.builder()
                .followId(UUID.randomUUID().toString())
                .followerId(followerId)
                .followedId(followedId)
                .createdAt(Instant.now())
                .build();
        followRepository.save(follow);
        log.info("关注关系已创建: follower={}, followed={}", followerId, followedId);

        boolean mutual = followRepository.existsByFollowerIdAndFollowedId(followedId, followerId);
        boolean friendshipCreated = false;

        if (mutual) {
            if (friendshipRepository.existsByUserIdAndFriendUserId(followerId, followedId)) {
                log.info("互关但双方已是好友，跳过创建: userA={}, userB={}", followerId, followedId);
            } else {
                createBilateralFriendship(followerId, followedId, FriendshipSource.mutualFollow);
                friendshipCreated = true;
                log.info("互相关注升级为好友: userA={}, userB={}", followerId, followedId);
            }
        }

        SocialDtos.FollowRelation result = new SocialDtos.FollowRelation();
        result.setTargetUserId(followedId);
        result.setFollowing(true);
        result.setMutual(mutual);
        result.setFriendshipCreated(friendshipCreated);
        return result;
    }

    /**
     * 取消关注用户。仅互关形成的好友关系随之解除。
     *
     * <p>前置条件：{@code followerId} 已关注 {@code followedId}。
     *
     * <p>后置条件：关注关系已删除。若好友关系来源为 mutualFollow，则双向好友关系已解除。
     *
     * @param followerId 关注者 ID
     * @param followedId 被关注者 ID
     * @return 关注关系结果
     * @throws BusinessException 目标不存在或未关注时抛出
     */
    public SocialDtos.FollowRelation unfollowUser(String followerId, String followedId) {
        if (!userRepository.existsById(followedId)) {
            log.warn("目标用户不存在: followedId={}", followedId);
            throw new BusinessException(USER_NOT_VISIBLE, "User " + followedId + " is not visible");
        }

        if (!followRepository.existsByFollowerIdAndFollowedId(followerId, followedId)) {
            log.warn("关注关系不存在: follower={}, followed={}", followerId, followedId);
            throw new BusinessException(FOLLOW_NOT_FOUND, "Follow relation does not exist");
        }

        followRepository.deleteByFollowerIdAndFollowedId(followerId, followedId);
        log.info("关注关系已解除: follower={}, followed={}", followerId, followedId);

        boolean friendshipCleared = false;

        if (friendshipRepository.existsByUserIdAndFriendUserId(followerId, followedId)) {
            Friendship friendship = friendshipRepository
                    .findByUserIdAndFriendUserId(followerId, followedId)
                    .orElse(null);
            if (friendship != null && FriendshipSource.mutualFollow == friendship.getSource()) {
                friendshipRepository.deleteBilateral(followerId, followedId);
                friendshipCleared = true;
                log.info("互关好友关系已解除: userA={}, userB={}", followerId, followedId);
            }
        }

        SocialDtos.FollowRelation result = new SocialDtos.FollowRelation();
        result.setTargetUserId(followedId);
        result.setFollowing(false);
        result.setMutual(false);
        result.setFriendshipCreated(!friendshipCleared);
        return result;
    }

    // ========================================
    // Friend Requests
    // ========================================

    /**
     * 发送好友申请。
     *
     * <p>前置条件：双方不是好友且不存在黑名单关系，不存在待处理的有效申请。
     *
     * <p>后置条件：好友申请已创建，状态为 pending。
     *
     * @param requesterId  申请人 ID
     * @param targetUserId 目标用户 ID
     * @param source       申请来源
     * @param message      附言
     * @return 好友申请记录
     * @throws BusinessException 目标不存在、黑名单、已是好友或重复申请时抛出
     */
    public SocialDtos.FriendRequest createFriendRequest(
            String requesterId, String targetUserId, String source, String message) {
        if (!userRepository.existsById(targetUserId)) {
            throw new BusinessException(USER_NOT_VISIBLE, "User " + targetUserId + " is not visible");
        }

        if (blacklistRepository.existsByBlockerIdAndBlockedUserId(targetUserId, requesterId)
                || blacklistRepository.existsByBlockerIdAndBlockedUserId(requesterId, targetUserId)) {
            throw new BusinessException(BLACKLIST_RELATION_EXISTS, "Blacklist relation blocks this operation");
        }

        if (friendshipRepository.existsByUserIdAndFriendUserId(requesterId, targetUserId)) {
            throw new BusinessException(FRIENDSHIP_STATE_INVALID, "Friendship state does not allow this operation");
        }

        if (friendRequestRepository.existsByRequesterIdAndTargetUserIdAndStatus(
                requesterId, targetUserId, FriendRequestStatus.pending)) {
            throw new BusinessException(DUPLICATE_FRIEND_REQUEST, "Friend request already exists");
        }

        FriendRequest request = FriendRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .requesterId(requesterId)
                .targetUserId(targetUserId)
                .source(io.github.layjason.mayoistar.entity.social.FriendRequestSource.valueOf(source))
                .message(message)
                .status(FriendRequestStatus.pending)
                .createdAt(Instant.now())
                .build();
        friendRequestRepository.save(request);
        log.info("好友申请已发送: from={}, to={}", requesterId, targetUserId);

        SocialDtos.FriendRequest result = toFriendRequestDto(request);
        notificationService.notifyFriendRequestCreated(result);
        return result;
    }

    /**
     * 查询已发送的好友申请。
     *
     * <p>前置条件：{@code userId} 为有效用户。
     *
     * <p>后置条件：返回分页的已发送申请列表。
     *
     * @param userId   用户 ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.FriendRequest> listSentFriendRequests(String userId, int page, int pageSize) {
        var pageResult = friendRequestRepository.findByRequesterIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page - 1, pageSize));
        return toPageResult(pageResult, this::toFriendRequestDto);
    }

    /**
     * 查询已收到的好友申请。
     *
     * <p>前置条件：{@code userId} 为有效用户。
     *
     * <p>后置条件：返回分页的已收到申请列表。
     *
     * @param userId   用户 ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.FriendRequest> listReceivedFriendRequests(String userId, int page, int pageSize) {
        var pageResult = friendRequestRepository.findByTargetUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page - 1, pageSize));
        return toPageResult(pageResult, this::toFriendRequestDto);
    }

    /**
     * 处理好友申请。
     *
     * <p>前置条件：{@code userId} 为申请接收人，申请状态为 pending。
     *
     * <p>后置条件：申请状态已更新。若同意则创建双向好友关系。
     *
     * @param requestId 申请 ID
     * @param userId    当前用户 ID（申请接收人）
     * @param accepted  是否同意
     * @return 更新后的申请记录
     * @throws BusinessException 申请状态不允许处理时抛出
     */
    public SocialDtos.FriendRequest decideFriendRequest(String requestId, String userId, boolean accepted) {
        FriendRequest request = friendRequestRepository
                .findByRequestIdAndTargetUserIdAndStatus(requestId, userId, FriendRequestStatus.pending)
                .orElseThrow(() -> {
                    log.warn("好友申请状态不允许操作: requestId={}, userId={}", requestId, userId);
                    return new BusinessException(
                            FRIEND_REQUEST_STATE_INVALID, "Friend request state does not allow this operation");
                });

        request.setStatus(accepted ? FriendRequestStatus.accepted : FriendRequestStatus.rejected);
        friendRequestRepository.save(request);

        if (accepted) {
            createBilateralFriendship(
                    request.getRequesterId(), request.getTargetUserId(), FriendshipSource.manualRequest);
            log.info("好友申请已同意，好友关系已建立: {} <-> {}", request.getRequesterId(), request.getTargetUserId());
        } else {
            log.info("好友申请已拒绝: requestId={}", requestId);
        }

        return toFriendRequestDto(request);
    }

    // ========================================
    // Friends
    // ========================================

    /**
     * 获取好友列表。
     *
     * <p>前置条件：{@code userId} 为有效用户。
     *
     * <p>后置条件：返回分页的好友列表。
     *
     * @param userId   用户 ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.FriendItem> listFriends(String userId, int page, int pageSize) {
        var pageResult =
                friendshipRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, pageSize));
        return toPageResult(pageResult, this::toFriendItemDto);
    }

    /**
     * 更新好友备注和分组标签。
     *
     * <p>前置条件：{@code userId} 与 {@code friendUserId} 为好友关系。
     *
     * <p>后置条件：调用方侧的好友备注和分组已更新。
     *
     * @param userId       当前用户 ID
     * @param friendUserId 好友用户 ID
     * @param remark       备注
     * @param groupTags    分组标签
     * @return 更新后的好友信息
     * @throws BusinessException 好友关系不存在时抛出
     */
    public SocialDtos.FriendItem updateFriendRemark(
            String userId, String friendUserId, String remark, List<String> groupTags) {
        Friendship friendship = friendshipRepository
                .findByUserIdAndFriendUserId(userId, friendUserId)
                .orElseThrow(() -> {
                    log.warn("好友关系不存在: userId={}, friendUserId={}", userId, friendUserId);
                    return new BusinessException(
                            FRIENDSHIP_STATE_INVALID, "Friendship state does not allow this operation");
                });

        if (remark != null) {
            friendship.setRemark(remark);
        }
        if (groupTags != null) {
            friendship.setGroupTags(groupTags);
        }
        friendshipRepository.save(friendship);
        log.info("好友备注已更新: userId={}, friendUserId={}", userId, friendUserId);
        return toFriendItemDto(friendship);
    }

    /**
     * 删除好友，双向解除好友关系。
     *
     * <p>前置条件：{@code userId} 与 {@code friendUserId} 为好友关系。
     *
     * <p>后置条件：双向好友关系已删除。
     *
     * @param userId       当前用户 ID
     * @param friendUserId 好友用户 ID
     * @throws BusinessException 好友关系不存在时抛出
     */
    public void deleteFriend(String userId, String friendUserId) {
        if (!friendshipRepository.existsByUserIdAndFriendUserId(userId, friendUserId)) {
            throw new BusinessException(FRIENDSHIP_STATE_INVALID, "Friendship state does not allow this operation");
        }
        friendshipRepository.deleteBilateral(userId, friendUserId);
        log.info("好友关系已双向解除: userA={}, userB={}", userId, friendUserId);
    }

    // ========================================
    // Private Helpers
    // ========================================

    /**
     * 创建双向好友关系。
     *
     * <p>前置条件：{@code userIdA} 与 {@code userIdB} 为不同的有效用户。
     *
     * <p>后置条件：两条 Friendships 记录（A→B 和 B→A）已持久化。
     */
    private void createBilateralFriendship(String userIdA, String userIdB, FriendshipSource source) {
        Instant now = Instant.now();

        Friendship friendshipAB = Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(userIdA)
                .friendUserId(userIdB)
                .source(source)
                .createdAt(now)
                .build();

        Friendship friendshipBA = Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(userIdB)
                .friendUserId(userIdA)
                .source(source)
                .createdAt(now)
                .build();

        friendshipRepository.save(friendshipAB);
        friendshipRepository.save(friendshipBA);
    }

    private IdentityDtos.PublicUserProfile toPublicUserProfile(User user, PersonalProfile profile) {
        IdentityDtos.PublicUserProfile dto = new IdentityDtos.PublicUserProfile();
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickname());
        dto.setGender(profile.getGender());
        dto.setBirthday(profile.getBirthday());
        dto.setSignature(profile.getSignature());
        dto.setInterestTags(profile.getInterestTags() != null ? profile.getInterestTags() : Collections.emptyList());
        dto.setReputationScore(profile.getReputationScore() != null ? profile.getReputationScore() : 100);
        dto.setKind(user.getKind());

        if (profile.getAvatar() != null) {
            dto.setAvatar(toMediaFileDto(profile.getAvatar()));
        }

        return dto;
    }

    private SocialDtos.FriendRequest toFriendRequestDto(FriendRequest entity) {
        SocialDtos.FriendRequest dto = new SocialDtos.FriendRequest();
        dto.setRequestId(entity.getRequestId());
        dto.setRequesterId(entity.getRequesterId());
        dto.setTargetUserId(entity.getTargetUserId());
        dto.setSource(entity.getSource());
        dto.setMessage(entity.getMessage());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt().toString());
        return dto;
    }

    private SocialDtos.FriendItem toFriendItemDto(Friendship entity) {
        SocialDtos.FriendItem dto = new SocialDtos.FriendItem();
        dto.setUserId(entity.getFriendUserId());
        dto.setRemark(entity.getRemark());
        dto.setGroupTags(entity.getGroupTags() != null ? entity.getGroupTags() : Collections.emptyList());
        dto.setSource(entity.getSource());

        userRepository.findById(entity.getFriendUserId()).ifPresent(user -> {
            dto.setNickname(user.getNickname());
        });

        personalProfileRepository.findByUserId(entity.getFriendUserId()).ifPresent(profile -> {
            if (profile.getAvatar() != null) {
                dto.setAvatar(toMediaFileDto(profile.getAvatar()));
            }
        });

        return dto;
    }

    private CommonDtos.MediaFile toMediaFileDto(io.github.layjason.mayoistar.entity.common.MediaFile entity) {
        CommonDtos.MediaFile dto = new CommonDtos.MediaFile();
        dto.setMediaId(entity.getMediaId());
        dto.setFileName(entity.getFileName());
        dto.setContentType(entity.getContentType());
        dto.setSizeBytes(entity.getSizeBytes());
        dto.setUsage(entity.getUsage());
        dto.setUrl(entity.getUrl());
        dto.setUploadedAt(entity.getUploadedAt().toString());
        return dto;
    }

    private <T, E> PageResult<T> toPageResult(
            org.springframework.data.domain.Page<E> springPage, java.util.function.Function<E, T> mapper) {
        List<T> items = springPage.getContent().stream().map(mapper).toList();
        return new PageResult<>(
                items,
                springPage.getTotalElements(),
                springPage.getNumber() + 1,
                springPage.getSize(),
                springPage.getTotalPages());
    }
}

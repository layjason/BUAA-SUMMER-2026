package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.social.Follow;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.entity.social.FriendshipSource;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.FollowRepository;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关注关系服务实现。
 *
 * <p>类职责：实现单向关注、互关升级好友、取消互关好友以及关注/粉丝列表查询。
 *
 * <p>不变量：关注关系不可自引用；互关好友始终以双向 Friendship 记录表示。
 */
@Slf4j
@Service
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final FriendshipRepository friendshipRepository;
    private final BlacklistRepository blacklistRepository;
    private final UserRepository userRepository;
    private final PersonalProfileRepository personalProfileRepository;

    public FollowServiceImpl(
            FollowRepository followRepository,
            FriendshipRepository friendshipRepository,
            BlacklistRepository blacklistRepository,
            UserRepository userRepository,
            PersonalProfileRepository personalProfileRepository) {
        this.followRepository = followRepository;
        this.friendshipRepository = friendshipRepository;
        this.blacklistRepository = blacklistRepository;
        this.userRepository = userRepository;
        this.personalProfileRepository = personalProfileRepository;
    }

    /**
     * 关注目标用户，并在互关时创建好友关系。
     *
     * <p>前置条件：目标用户存在，不能关注自己，双方无黑名单关系，当前尚未关注目标用户。
     *
     * <p>后置条件：关注关系已保存；若已互相关注且不是好友，双向 Friendship 已保存。
     */
    @Override
    @Transactional
    public SocialDtos.FollowRelation followUser(String followerId, String followedId) {
        validateFollowTarget(followerId, followedId);
        if (followRepository.existsByFollowerIdAndFollowedId(followerId, followedId)) {
            throw new BusinessException(40002, "Follow relation already exists");
        }

        Follow follow = Follow.builder()
                .followId(UUID.randomUUID().toString())
                .followerId(followerId)
                .followedId(followedId)
                .createdAt(Instant.now())
                .build();
        followRepository.save(follow);

        boolean mutual = followRepository.existsByFollowerIdAndFollowedId(followedId, followerId);
        boolean friendshipCreated = false;
        if (mutual && !friendshipRepository.existsByUserIdAndFriendUserId(followerId, followedId)) {
            createBilateralFriendship(followerId, followedId, FriendshipSource.mutualFollow);
            friendshipCreated = true;
        }

        log.info("关注关系创建成功: follower={}, followed={}, mutual={}", followerId, followedId, mutual);
        return toFollowRelation(followedId, true, mutual, friendshipCreated);
    }

    /**
     * 取消关注目标用户，并在必要时解除互关好友关系。
     *
     * <p>前置条件：目标用户存在，且当前用户已关注目标用户。
     *
     * <p>后置条件：关注关系已删除；若 Friendship 来源为 mutualFollow，双向好友关系已删除。
     */
    @Override
    @Transactional
    public SocialDtos.FollowRelation unfollowUser(String followerId, String followedId) {
        if (!userRepository.existsById(followedId)) {
            throw new BusinessException(40000, "User " + followedId + " is not visible");
        }
        if (!followRepository.existsByFollowerIdAndFollowedId(followerId, followedId)) {
            throw new BusinessException(40003, "Follow relation does not exist");
        }

        followRepository.deleteByFollowerIdAndFollowedId(followerId, followedId);

        boolean friendshipCleared = friendshipRepository
                .findByUserIdAndFriendUserId(followerId, followedId)
                .filter(friendship -> friendship.getSource() == FriendshipSource.mutualFollow)
                .map(friendship -> {
                    friendshipRepository.deleteBilateral(followerId, followedId);
                    return true;
                })
                .orElse(false);

        log.info("关注关系删除成功: follower={}, followed={}, friendshipCleared={}", followerId, followedId, friendshipCleared);
        return toFollowRelation(followedId, false, false, false);
    }

    /**
     * 查询当前用户关注的人。
     *
     * <p>前置条件：page 从 1 开始，pageSize 为正数。
     *
     * <p>后置条件：返回关注对象列表，mutual 表示对方是否也关注当前用户。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.FollowItem> listFollows(String userId, int page, int pageSize) {
        var follows = followRepository.findByFollowerIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, pageSize));
        return toPageResult(follows, page, pageSize, true, userId);
    }

    /**
     * 查询当前用户的粉丝。
     *
     * <p>前置条件：page 从 1 开始，pageSize 为正数。
     *
     * <p>后置条件：返回粉丝列表，mutual 表示当前用户是否也关注该粉丝。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.FollowItem> listFollowers(String userId, int page, int pageSize) {
        var followers =
                followRepository.findByFollowedIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, pageSize));
        return toPageResult(followers, page, pageSize, false, userId);
    }

    /**
     * 校验关注目标。
     *
     * <p>前置条件：followerId 与 followedId 为调用方传入的用户 ID。
     *
     * <p>后置条件：校验通过则不修改任何状态；校验失败抛出业务异常。
     */
    private void validateFollowTarget(String followerId, String followedId) {
        if (followerId.equals(followedId)) {
            throw new BusinessException(40000, "User " + followedId + " is not visible");
        }
        if (!userRepository.existsById(followedId)) {
            throw new BusinessException(40000, "User " + followedId + " is not visible");
        }
        if (blacklistRepository.existsByBlockerIdAndBlockedUserId(followerId, followedId)
                || blacklistRepository.existsByBlockerIdAndBlockedUserId(followedId, followerId)) {
            throw new BusinessException(40001, "Blacklist relation blocks this operation");
        }
    }

    /**
     * 创建双向好友关系。
     *
     * <p>前置条件：两名用户尚不存在好友关系。
     *
     * <p>后置条件：A→B 与 B→A 两条 Friendship 记录已保存。
     */
    private void createBilateralFriendship(String userIdA, String userIdB, FriendshipSource source) {
        Instant now = Instant.now();
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(userIdA)
                .friendUserId(userIdB)
                .source(source)
                .createdAt(now)
                .build());
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(userIdB)
                .friendUserId(userIdA)
                .source(source)
                .createdAt(now)
                .build());
    }

    private SocialDtos.FollowRelation toFollowRelation(
            String targetUserId, boolean following, boolean mutual, boolean friendshipCreated) {
        SocialDtos.FollowRelation result = new SocialDtos.FollowRelation();
        result.setTargetUserId(targetUserId);
        result.setFollowing(following);
        result.setMutual(mutual);
        result.setFriendshipCreated(friendshipCreated);
        return result;
    }

    private PageResult<SocialDtos.FollowItem> toPageResult(
            Page<Follow> page, int pageNum, int pageSize, boolean listingFollows, String currentUserId) {
        List<SocialDtos.FollowItem> items = page.getContent().stream()
                .map(follow -> toFollowItem(follow, listingFollows, currentUserId))
                .toList();
        PageResult<SocialDtos.FollowItem> result = new PageResult<>();
        result.setItems(items);
        result.setTotal(page.getTotalElements());
        result.setPage(pageNum);
        result.setPageSize(pageSize);
        result.setTotalPages(page.getTotalPages());
        return result;
    }

    private SocialDtos.FollowItem toFollowItem(Follow follow, boolean listingFollows, String currentUserId) {
        String relatedUserId = listingFollows ? follow.getFollowedId() : follow.getFollowerId();
        SocialDtos.FollowItem item = new SocialDtos.FollowItem();
        item.setUserId(relatedUserId);
        item.setFollowedAt(follow.getCreatedAt().toString());
        item.setMutual(
                listingFollows
                        ? followRepository.existsByFollowerIdAndFollowedId(relatedUserId, currentUserId)
                        : followRepository.existsByFollowerIdAndFollowedId(currentUserId, relatedUserId));

        userRepository.findById(relatedUserId).ifPresent(user -> {
            item.setNickname(user.getNickname());
            personalProfileRepository.findByUserId(user.getUserId()).ifPresent(profile -> {
                if (profile.getAvatar() != null) {
                    item.setAvatar(toMediaFileDto(profile.getAvatar()));
                }
            });
        });

        if (item.getNickname() == null) {
            item.setNickname("unknown");
        }
        return item;
    }

    private CommonDtos.MediaFile toMediaFileDto(MediaFile entity) {
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
}

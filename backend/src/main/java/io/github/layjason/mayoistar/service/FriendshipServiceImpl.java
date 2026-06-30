package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 好友管理服务实现。
 *
 * <p>类职责：实现好友列表查询、备注更新、删除好友的业务逻辑。
 *
 * <p>不变量：所有修改操作均校验好友关系存在性。
 */
@Slf4j
@Service
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendshipServiceImpl(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    /**
     * 分页查询当前用户的好友列表。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页 FriendItem，含昵称。
     */
    @Override
    public PageResult<SocialDtos.FriendItem> listFriends(String userId, int page, int pageSize) {
        var friendshipPage =
                friendshipRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, pageSize));

        List<SocialDtos.FriendItem> items =
                friendshipPage.getContent().stream().map(this::toFriendItem).toList();

        PageResult<SocialDtos.FriendItem> result = new PageResult<>();
        result.setItems(items);
        result.setTotal(friendshipPage.getTotalElements());
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setTotalPages(friendshipPage.getTotalPages());
        return result;
    }

    /**
     * 更新好友的备注和分组标签。
     *
     * <p>前置条件：好友关系存在。
     *
     * <p>后置条件：remark 和 groupTags 已更新。
     */
    @Override
    @Transactional
    public SocialDtos.FriendItem updateFriendRemark(
            String userId, String friendUserId, String remark, List<String> groupTags) {
        Friendship friendship = friendshipRepository
                .findByUserIdAndFriendUserId(userId, friendUserId)
                .orElseThrow(() -> new BusinessException(40004, "Friendship state does not allow this operation"));

        if (remark != null) {
            friendship.setRemark(remark);
        }
        if (groupTags != null) {
            friendship.setGroupTags(groupTags);
        }
        friendshipRepository.save(friendship);

        log.info("好友备注更新: user={}, friend={}", userId, friendUserId);
        return toFriendItem(friendship);
    }

    /**
     * 删除好友，双向关系同时解除。
     *
     * <p>前置条件：好友关系存在。
     *
     * <p>后置条件：A→B 和 B→A 两笔 Friendship 记录均删除。
     */
    @Override
    @Transactional
    public void deleteFriend(String userId, String friendUserId) {
        if (!friendshipRepository.existsByUserIdAndFriendUserId(userId, friendUserId)) {
            throw new BusinessException(40004, "Friendship state does not allow this operation");
        }

        friendshipRepository.deleteByUserIdAndFriendUserId(userId, friendUserId);
        friendshipRepository.deleteByUserIdAndFriendUserId(friendUserId, userId);

        log.info("好友删除成功: {} <-> {}", userId, friendUserId);
    }

    private SocialDtos.FriendItem toFriendItem(Friendship friendship) {
        SocialDtos.FriendItem item = new SocialDtos.FriendItem();
        item.setUserId(friendship.getFriendUserId());
        item.setNickname(userRepository
                .findById(friendship.getFriendUserId())
                .map(User::getNickname)
                .orElse("unknown"));
        item.setRemark(friendship.getRemark());
        item.setGroupTags(friendship.getGroupTags());
        item.setSource(friendship.getSource());
        return item;
    }
}

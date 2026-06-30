package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.social.Blacklist;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
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
 * 黑名单服务实现。
 *
 * <p>类职责：实现拉黑、取消拉黑、查询黑名单列表的业务逻辑。
 *
 * <p>不变量：拉黑操作前校验目标用户存在性、非自引用、无重复关系。
 */
@Slf4j
@Service
public class BlacklistServiceImpl implements BlacklistService {

    private final BlacklistRepository blacklistRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    public BlacklistServiceImpl(
            BlacklistRepository blacklistRepository,
            UserRepository userRepository,
            FriendshipRepository friendshipRepository) {
        this.blacklistRepository = blacklistRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
    }

    /**
     * 将目标用户加入黑名单，同时删除双向好友关系。
     *
     * <p>前置条件：targetUserId 对应有效用户，且不是当前用户，且尚未在黑名单中。
     *
     * <p>后置条件：黑名单记录已持久化；若存在好友关系则双向删除。
     *
     * @param currentUserId 当前用户 ID
     * @param targetUserId  目标用户 ID
     */
    @Override
    @Transactional
    public void blockUser(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(40001, "Blacklist relation blocks this operation");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new BusinessException(40000, "User " + targetUserId + " is not visible");
        }
        if (blacklistRepository.existsByBlockerIdAndBlockedUserId(currentUserId, targetUserId)) {
            throw new BusinessException(40001, "Blacklist relation blocks this operation");
        }

        Blacklist record = Blacklist.builder()
                .blacklistId(UUID.randomUUID().toString())
                .blockerId(currentUserId)
                .blockedUserId(targetUserId)
                .createdAt(Instant.now())
                .build();
        blacklistRepository.save(record);

        friendshipRepository.deleteByUserIdAndFriendUserId(currentUserId, targetUserId);
        friendshipRepository.deleteByUserIdAndFriendUserId(targetUserId, currentUserId);

        log.info("拉黑成功: blocker={}, blocked={}", currentUserId, targetUserId);
    }

    /**
     * 将目标用户移出黑名单。
     *
     * <p>前置条件：黑名单关系存在。
     *
     * <p>后置条件：对应黑名单记录删除。
     *
     * @param currentUserId 当前用户 ID
     * @param targetUserId  目标用户 ID
     */
    @Override
    @Transactional
    public void unblockUser(String currentUserId, String targetUserId) {
        if (!blacklistRepository.existsByBlockerIdAndBlockedUserId(currentUserId, targetUserId)) {
            throw new BusinessException(40019, "Blacklist relation does not exist");
        }

        blacklistRepository.deleteByBlockerIdAndBlockedUserId(currentUserId, targetUserId);

        log.info("取消拉黑成功: blocker={}, blocked={}", currentUserId, targetUserId);
    }

    /**
     * 分页查询当前用户的黑名单列表。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页 BlacklistItem，含昵称。
     *
     * @param currentUserId 当前用户 ID
     * @param page          页码（从 1 开始）
     * @param pageSize      每页条数
     * @return 黑名单分页结果
     */
    @Override
    public PageResult<SocialDtos.BlacklistItem> listBlacklist(String currentUserId, int page, int pageSize) {
        var blacklistPage = blacklistRepository.findByBlockerIdOrderByCreatedAtDesc(
                currentUserId, PageRequest.of(page - 1, pageSize));

        List<SocialDtos.BlacklistItem> items =
                blacklistPage.getContent().stream().map(this::toBlacklistItem).toList();

        PageResult<SocialDtos.BlacklistItem> result = new PageResult<>();
        result.setItems(items);
        result.setTotal(blacklistPage.getTotalElements());
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setTotalPages(blacklistPage.getTotalPages());
        return result;
    }

    /**
     * 将 Blacklist 实体转换为 BlacklistItem DTO。
     *
     * <p>前置条件：record.blockedUserId 对应有效用户。
     *
     * <p>后置条件：返回填充了 userId、nickname、blockedAt 的 DTO。
     */
    private SocialDtos.BlacklistItem toBlacklistItem(Blacklist record) {
        SocialDtos.BlacklistItem item = new SocialDtos.BlacklistItem();
        item.setUserId(record.getBlockedUserId());
        item.setNickname(userRepository
                .findById(record.getBlockedUserId())
                .map(User::getNickname)
                .orElse("unknown"));
        item.setBlockedAt(record.getCreatedAt().toString());
        return item;
    }
}

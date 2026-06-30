package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ChatMessageRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.MessageReadRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
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
    private final PersonalProfileRepository personalProfileRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageReadRepository messageReadRepository;

    public FriendshipServiceImpl(
            FriendshipRepository friendshipRepository,
            UserRepository userRepository,
            PersonalProfileRepository personalProfileRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            ChatMessageRepository chatMessageRepository,
            MessageReadRepository messageReadRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.personalProfileRepository = personalProfileRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messageReadRepository = messageReadRepository;
    }

    /**
     * 分页查询当前用户的好友列表。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页 FriendItem，含昵称。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.FriendItem> listFriends(String userId, int page, int pageSize, String keyword) {
        var friendshipPage = (keyword != null && !keyword.isBlank())
                ? friendshipRepository.findByUserIdAndFriendNicknameContaining(
                        userId, keyword, PageRequest.of(page - 1, pageSize))
                : friendshipRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, pageSize));

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
     * 删除好友，双向关系同时解除，并清理关联的会话数据。
     *
     * <p>前置条件：好友关系存在。
     *
     * <p>后置条件：双向 Friendship 记录删除；共享的好友会话及其中消息、已读状态、成员关系均清理。
     */
    @Override
    @Transactional
    public void deleteFriend(String userId, String friendUserId) {
        if (!friendshipRepository.existsByUserIdAndFriendUserId(userId, friendUserId)) {
            throw new BusinessException(40004, "Friendship state does not allow this operation");
        }

        List<String> commonConversationIds =
                conversationMemberRepository.findCommonConversationIds(userId, friendUserId);

        for (String conversationId : commonConversationIds) {
            conversationRepository.findById(conversationId).ifPresent(conversation -> {
                if (conversation.getKind() == ConversationKind.friend) {
                    messageReadRepository.deleteByConversationId(conversationId);
                    chatMessageRepository.deleteByConversationId(conversationId);
                    conversationMemberRepository.deleteByConversationId(conversationId);
                    conversationRepository.delete(conversation);
                    log.info("好友会话已清理: conversationId={}", conversationId);
                }
            });
        }

        friendshipRepository.deleteByUserIdAndFriendUserId(userId, friendUserId);
        friendshipRepository.deleteByUserIdAndFriendUserId(friendUserId, userId);

        log.info("好友删除成功: {} <-> {}", userId, friendUserId);
    }

    private SocialDtos.FriendItem toFriendItem(Friendship friendship) {
        SocialDtos.FriendItem item = new SocialDtos.FriendItem();
        item.setUserId(friendship.getFriendUserId());
        item.setRemark(friendship.getRemark());
        item.setGroupTags(friendship.getGroupTags());
        item.setSource(friendship.getSource());

        userRepository.findById(friendship.getFriendUserId()).ifPresent(user -> {
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

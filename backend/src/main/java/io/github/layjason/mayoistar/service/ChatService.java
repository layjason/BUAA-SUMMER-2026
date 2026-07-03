package io.github.layjason.mayoistar.service;

import static io.github.layjason.mayoistar.exception.ErrorCodes.ANNOUNCEMENT_NOT_VISIBLE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.ANNOUNCEMENT_PERMISSION_DENIED;
import static io.github.layjason.mayoistar.exception.ErrorCodes.CONVERSATION_MEMBER_REQUIRED;
import static io.github.layjason.mayoistar.exception.ErrorCodes.FORWARD_TARGET_UNAVAILABLE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.MEDIA_REFERENCE_INVALID;
import static io.github.layjason.mayoistar.exception.ErrorCodes.MESSAGE_NOT_VISIBLE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.MESSAGE_RECALL_EXPIRED;
import static io.github.layjason.mayoistar.exception.ErrorCodes.MESSAGE_SENDER_REQUIRED;
import static io.github.layjason.mayoistar.exception.ErrorCodes.POLL_OPTIONS_INVALID;
import static io.github.layjason.mayoistar.exception.ErrorCodes.POLL_UNAVAILABLE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_MEMBER_REQUIRED;

import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.chat.ChatMessage;
import io.github.layjason.mayoistar.entity.chat.Conversation;
import io.github.layjason.mayoistar.entity.chat.MessageKind;
import io.github.layjason.mayoistar.entity.chat.MessageRead;
import io.github.layjason.mayoistar.entity.chat.MessageReadStatus;
import io.github.layjason.mayoistar.entity.chat.PollOption;
import io.github.layjason.mayoistar.entity.chat.PollVote;
import io.github.layjason.mayoistar.entity.chat.TeamAnnouncement;
import io.github.layjason.mayoistar.entity.chat.TeamAnnouncementRead;
import io.github.layjason.mayoistar.entity.chat.TeamPoll;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.social.TeamMemberRole;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ChatMessageRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.MessageReadRepository;
import io.github.layjason.mayoistar.repository.PollOptionRepository;
import io.github.layjason.mayoistar.repository.PollVoteRepository;
import io.github.layjason.mayoistar.repository.TeamAnnouncementReadRepository;
import io.github.layjason.mayoistar.repository.TeamAnnouncementRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamPollRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 即时通讯服务，管理消息发送、已读/未读、撤回和转发。
 *
 * <p>类职责：封装聊天消息的所有业务逻辑，包括 2 分钟撤回窗口校验、已读状态的逐用户追踪和消息转发。
 *
 * <p>类不变量：所有方法均在事务内执行。消息内容在撤回后仍保留用于审计。
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ChatService {

    private static final Duration RECALL_WINDOW = Duration.ofMinutes(2);

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationRepository conversationRepository;
    private final MessageReadRepository messageReadRepository;
    private final MediaFileRepository mediaFileRepository;
    private final NotificationService notificationService;
    private final TeamAnnouncementRepository teamAnnouncementRepository;
    private final TeamAnnouncementReadRepository teamAnnouncementReadRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamPollRepository teamPollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final MediaAccessService mediaAccessService;

    // ========================================
    // Send Message
    // ========================================

    /**
     * 发送消息，创建消息并初始化所有会话成员的已读状态。
     *
     * <p>前置条件：{@code senderId} 是 {@code conversationId} 会话的成员，消息内容与类型匹配。
     *
     * <p>后置条件：消息已持久化，所有会话成员的 message_reads 记录已创建。
     *
     * @param conversationId 会话 ID
     * @param senderId       发送者 ID
     * @param request        消息发送请求
     * @return 已创建的消息
     * @throws BusinessException 会话不可见、非成员或内容类型不匹配时抛出
     */
    public ChatDtos.ChatMessage sendMessage(
            String conversationId, String senderId, ChatDtos.SendMessageRequest request) {
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)) {
            log.warn("用户非会话成员: conversationId={}, userId={}", conversationId, senderId);
            throw new BusinessException(CONVERSATION_MEMBER_REQUIRED, "Conversation membership is required");
        }

        validateMessageContent(request);

        ChatMessage message = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .senderId(senderId)
                .kind(request.getKind())
                .text(request.getText())
                .imageMediaId(request.getImageMediaId())
                .mentionedUserIds(request.getMentionedUserIds())
                .mentionAll(request.getMentionAll())
                .recalled(false)
                .sentAt(Instant.now())
                .build();

        if (request.getKind() == MessageKind.location && request.getLocation() != null) {
            CommonDtos.LocationInfo loc = request.getLocation();
            if (loc.getPoint() != null) {
                message.setLocationLon(loc.getPoint().getLongitude());
                message.setLocationLat(loc.getPoint().getLatitude());
            }
            message.setLocationCity(loc.getCity());
            message.setLocationAddress(loc.getAddress());
            message.setLocationPlaceName(loc.getPlaceName());
        }

        // 保存消息并获取托管实体引用，确保后续懒加载代理可用
        ChatMessage savedMessage = chatMessageRepository.save(message);
        log.info(
                "消息已发送: messageId={}, conversationId={}, kind={}",
                savedMessage.getMessageId(),
                conversationId,
                request.getKind());

        // 图片消息需将访问策略从默认 owner 提升为 conversationMember，使会话所有成员可查看
        if (request.getKind() == MessageKind.image && request.getImageMediaId() != null) {
            mediaAccessService.updateAccessPolicy(
                    request.getImageMediaId(), MediaAccessPolicy.conversationMember, conversationId);
        }

        initializeReadStatus(savedMessage.getMessageId(), conversationId, senderId);

        ChatDtos.ChatMessage result = toChatMessageDto(savedMessage, MessageReadStatus.read);
        notificationService.notifyMessageCreated(result, getRecipientUserIds(conversationId, senderId));
        return result;
    }

    // ========================================
    // List Messages
    // ========================================

    /**
     * 分页获取会话消息列表，每条消息附带当前用户的已读状态。
     *
     * <p>前置条件：{@code userId} 是 {@code conversationId} 会话的成员。
     *
     * <p>后置条件：返回分页消息列表，已撤回消息仅展示撤回状态。
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID
     * @param page           页码
     * @param pageSize       每页数量
     * @return 分页结果
     * @throws BusinessException 会话不存在或用户非成员时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<ChatDtos.ChatMessage> listMessages(String conversationId, String userId, int page, int pageSize) {
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BusinessException(CONVERSATION_MEMBER_REQUIRED, "Conversation membership is required");
        }

        var pageResult = chatMessageRepository.findByConversationIdOrderBySentAtDesc(
                conversationId, PageRequest.of(page - 1, pageSize));

        List<String> messageIds =
                pageResult.getContent().stream().map(ChatMessage::getMessageId).collect(Collectors.toList());

        Map<String, MessageReadStatus> readStatusMap =
                messageReadRepository.findByMessageIdInAndUserId(messageIds, userId).stream()
                        .collect(Collectors.toMap(MessageRead::getMessageId, MessageRead::getStatus));

        List<ChatDtos.ChatMessage> items = pageResult.getContent().stream()
                .map(msg -> {
                    MessageReadStatus status = readStatusMap.getOrDefault(msg.getMessageId(), MessageReadStatus.unread);
                    return toChatMessageDto(msg, status);
                })
                .collect(Collectors.toList());

        return new PageResult<>(
                items,
                pageResult.getTotalElements(),
                pageResult.getNumber() + 1,
                pageResult.getSize(),
                pageResult.getTotalPages());
    }

    // ========================================
    // List Conversations
    // ========================================

    /**
     * 分页获取当前用户的会话列表，含未读计数和最后一条消息预览。
     *
     * <p>前置条件：{@code userId} 为有效用户。
     *
     * <p>后置条件：返回分页 ConversationSummary 列表，按更新时间倒序排列。
     *
     * @param userId   当前用户 ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ChatDtos.ConversationSummary> listConversations(String userId, int page, int pageSize) {
        var members = conversationMemberRepository.findByUserId(userId);

        List<ChatDtos.ConversationSummary> allItems = members.stream()
                .map(member -> conversationRepository
                        .findById(member.getConversationId())
                        .map(conv -> buildConversationSummary(conv, userId))
                        .orElse(null))
                .filter(summary -> summary != null)
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(Collectors.toList());

        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allItems.size());
        List<ChatDtos.ConversationSummary> pageItems =
                fromIndex < allItems.size() ? allItems.subList(fromIndex, toIndex) : List.of();

        return new PageResult<>(pageItems, (long) allItems.size(), page, pageSize, (int)
                Math.ceil((double) allItems.size() / pageSize));
    }

    private ChatDtos.ConversationSummary buildConversationSummary(Conversation conv, String userId) {
        ChatDtos.ConversationSummary summary = new ChatDtos.ConversationSummary();
        summary.setConversationId(conv.getConversationId());
        summary.setKind(conv.getKind());
        summary.setTitle(conv.getTitle());
        summary.setUpdatedAt(conv.getUpdatedAt().toString());

        var lastMessagePage = chatMessageRepository.findByConversationIdOrderBySentAtDesc(
                conv.getConversationId(), PageRequest.of(0, 1));
        if (!lastMessagePage.isEmpty()) {
            ChatMessage lastMsg = lastMessagePage.getContent().getFirst();
            if (Boolean.TRUE.equals(lastMsg.getRecalled())) {
                summary.setLastMessagePreview("[消息已撤回]");
            } else {
                summary.setLastMessagePreview(
                        lastMsg.getText() != null ? lastMsg.getText() : "[" + lastMsg.getKind() + "]");
            }
        }

        long unreadCount = 0L;
        var allMessages = chatMessageRepository.findByConversationIdOrderBySentAtDesc(
                conv.getConversationId(), PageRequest.of(0, Integer.MAX_VALUE));
        List<String> messageIds =
                allMessages.getContent().stream().map(ChatMessage::getMessageId).collect(Collectors.toList());
        if (!messageIds.isEmpty()) {
            unreadCount = messageReadRepository.findByMessageIdInAndUserId(messageIds, userId).stream()
                    .filter(mr -> mr.getStatus() == MessageReadStatus.unread)
                    .count();
        }
        summary.setUnreadCount((int) unreadCount);

        return summary;
    }

    // ========================================
    // Mark Messages Read
    // ========================================

    /**
     * 批量标记消息已读。只能标记非本人发送的消息。
     *
     * <p>前置条件：{@code userId} 为消息接收方，消息对调用方可见。
     *
     * <p>后置条件：指定消息的已读状态已更新。
     *
     * @param userId     当前用户 ID
     * @param messageIds 待标记消息 ID 列表
     * @return 更新后的消息列表
     * @throws BusinessException 消息不可见或非会话成员时抛出
     */
    public List<ChatDtos.ChatMessage> markMessagesRead(String userId, List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatMessage> messages = chatMessageRepository.findAllById(messageIds);
        if (messages.size() != messageIds.size()) {
            throw new BusinessException(MESSAGE_NOT_VISIBLE, "Message is not visible");
        }

        String conversationId = messages.getFirst().getConversationId();
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BusinessException(CONVERSATION_MEMBER_REQUIRED, "Conversation membership is required");
        }

        List<String> validMessageIds = messages.stream()
                .filter(msg -> !msg.getSenderId().equals(userId))
                .map(ChatMessage::getMessageId)
                .collect(Collectors.toList());

        messages.stream()
                .filter(msg -> msg.getImageMediaId() != null)
                .forEach(msg -> org.hibernate.Hibernate.initialize(msg.getImage()));

        if (!validMessageIds.isEmpty()) {
            int updated =
                    messageReadRepository.markAsRead(validMessageIds, userId, MessageReadStatus.read, Instant.now());
            log.info("消息已读标记完成: userId={}, count={}", userId, updated);
        }

        Map<String, MessageReadStatus> readStatusMap = messageReadRepository
                .findByMessageIdInAndUserId(
                        messages.stream().map(ChatMessage::getMessageId).collect(Collectors.toList()), userId)
                .stream()
                .collect(Collectors.toMap(MessageRead::getMessageId, MessageRead::getStatus));

        return messages.stream()
                .map(msg -> {
                    MessageReadStatus status = readStatusMap.getOrDefault(msg.getMessageId(), MessageReadStatus.unread);
                    return toChatMessageDto(msg, status);
                })
                .collect(Collectors.toList());
    }

    // ========================================
    // Recall Message
    // ========================================

    /**
     * 撤回消息。仅发送者可在 2 分钟内撤回。
     *
     * <p>前置条件：{@code userId} 为消息发送者，消息发送未超过 2 分钟。
     *
     * <p>后置条件：消息 recalled 标记为 true，内容保留。
     *
     * @param messageId 消息 ID
     * @param userId    当前用户 ID
     * @return 更新后的消息
     * @throws BusinessException 消息不可见、非发送者或超时时抛出
     */
    public ChatDtos.ChatMessage recallMessage(String messageId, String userId) {
        ChatMessage message = chatMessageRepository.findById(messageId).orElseThrow(() -> {
            log.warn("消息不存在: messageId={}", messageId);
            return new BusinessException(MESSAGE_NOT_VISIBLE, "Message " + messageId + " is not visible");
        });

        if (!message.getSenderId().equals(userId)) {
            log.warn("非发送者尝试撤回: messageId={}, userId={}", messageId, userId);
            throw new BusinessException(MESSAGE_SENDER_REQUIRED, "Message sender permission is required");
        }

        if (Duration.between(message.getSentAt(), Instant.now()).compareTo(RECALL_WINDOW) > 0) {
            log.warn("消息撤回超时: messageId={}, sentAt={}", messageId, message.getSentAt());
            throw new BusinessException(MESSAGE_RECALL_EXPIRED, "Message recall window has expired");
        }

        message.setRecalled(true);
        chatMessageRepository.save(message);
        log.info("消息已撤回: messageId={}", messageId);

        notificationService.notifyMessageRecalled(
                messageId, message.getConversationId(), getAllMemberUserIds(message.getConversationId()));
        return toChatMessageDto(message, MessageReadStatus.read);
    }

    // ========================================
    // Forward Message
    // ========================================

    /**
     * 转发消息到目标会话。原消息不变，各目标会话创建新消息。
     *
     * <p>前置条件：原消息可见，目标会话当前用户可发言。
     *
     * <p>后置条件：各目标会话中已创建新消息并初始化已读状态。
     *
     * @param originalMessageId     原消息 ID
     * @param senderId              转发者 ID
     * @param targetConversationIds 目标会话 ID 列表
     * @return 新创建的消息列表
     * @throws BusinessException 原消息不可见或目标会话不可用时时抛出
     */
    public List<ChatDtos.ChatMessage> forwardMessage(
            String originalMessageId, String senderId, List<String> targetConversationIds) {
        ChatMessage original = chatMessageRepository.findById(originalMessageId).orElseThrow(() -> {
            log.warn("原消息不存在: messageId={}", originalMessageId);
            return new BusinessException(MESSAGE_NOT_VISIBLE, "Message " + originalMessageId + " is not visible");
        });

        // 原始消息含图片时，预先加载原始 MediaFile 用于后续为各目标会话创建独立副本
        var originalImage = original.getImageMediaId() != null
                ? mediaFileRepository.findById(original.getImageMediaId()).orElse(null)
                : null;

        List<ChatDtos.ChatMessage> result = new ArrayList<>();

        for (String targetId : targetConversationIds) {
            if (!conversationMemberRepository.existsByConversationIdAndUserId(targetId, senderId)) {
                log.warn("转发目标会话用户非成员: conversationId={}, userId={}", targetId, senderId);
                throw new BusinessException(FORWARD_TARGET_UNAVAILABLE, "Forward target conversation is unavailable");
            }

            // 为图片消息创建目标会话独立的 MediaFile 副本，各会话权限互不干扰
            UUID targetImageMediaId;
            if (originalImage != null) {
                var copiedImage = mediaAccessService.copyForScope(
                        originalImage, senderId, MediaAccessPolicy.conversationMember, targetId);
                targetImageMediaId = copiedImage.getMediaId();
            } else {
                targetImageMediaId = original.getImageMediaId();
            }

            ChatMessage forwarded = ChatMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversationId(targetId)
                    .senderId(senderId)
                    .kind(original.getKind())
                    .text(original.getText())
                    .imageMediaId(targetImageMediaId)
                    .locationLon(original.getLocationLon())
                    .locationLat(original.getLocationLat())
                    .locationCity(original.getLocationCity())
                    .locationAddress(original.getLocationAddress())
                    .locationPlaceName(original.getLocationPlaceName())
                    .mentionedUserIds(original.getMentionedUserIds())
                    .mentionAll(original.getMentionAll())
                    .recalled(false)
                    .sentAt(Instant.now())
                    .build();

            // 保存消息并获取托管实体引用，确保后续懒加载代理可用
            ChatMessage savedForwarded = chatMessageRepository.save(forwarded);
            initializeReadStatus(savedForwarded.getMessageId(), targetId, senderId);
            ChatDtos.ChatMessage forwardedDto = toChatMessageDto(savedForwarded, MessageReadStatus.read);
            result.add(forwardedDto);
            notificationService.notifyMessageForwarded(forwardedDto, getRecipientUserIds(targetId, senderId));
            log.info("消息已转发: original={}, target={}", originalMessageId, targetId);
        }

        return result;
    }

    // ========================================
    // Team Announcements
    // ========================================

    /**
     * 发布群公告。
     *
     * <p>前置条件：{@code publisherId} 是 {@code teamId} 小队的队长或管理员。
     *
     * <p>后置条件：公告已持久化，所有小队成员的已读记录已创建（发布者标记为已读）。
     *
     * @param teamId      小队 ID
     * @param publisherId 发布者 ID
     * @param content      公告内容
     * @return 已创建的公告
     * @throws BusinessException 非小队成员或非管理角色时抛出
     */
    public ChatDtos.TeamAnnouncement publishAnnouncement(String teamId, String publisherId, String content) {
        var member = teamMemberRepository
                .findByTeamIdAndUserId(teamId, publisherId)
                .orElseThrow(() -> {
                    log.warn("非小队成员尝试发布公告: teamId={}, userId={}", teamId, publisherId);
                    return new BusinessException(TEAM_MEMBER_REQUIRED, "Team membership is required");
                });

        if (member.getRole() != TeamMemberRole.leader && member.getRole() != TeamMemberRole.admin) {
            log.warn("非管理角色尝试发布公告: teamId={}, userId={}, role={}", teamId, publisherId, member.getRole());
            throw new BusinessException(ANNOUNCEMENT_PERMISSION_DENIED, "Announcement operation is not allowed");
        }

        Instant now = Instant.now();
        TeamAnnouncement announcement = TeamAnnouncement.builder()
                .announcementId(UUID.randomUUID().toString())
                .teamId(teamId)
                .publisherId(publisherId)
                .content(content)
                .publishedAt(now)
                .build();
        teamAnnouncementRepository.save(announcement);

        var teamMembers = teamMemberRepository.findAllByTeamId(teamId);
        for (var tm : teamMembers) {
            TeamAnnouncementRead read = TeamAnnouncementRead.builder()
                    .readId(UUID.randomUUID().toString())
                    .announcementId(announcement.getAnnouncementId())
                    .userId(tm.getUserId())
                    .readAt(tm.getUserId().equals(publisherId) ? now : null)
                    .build();
            teamAnnouncementReadRepository.save(read);
        }

        log.info(
                "群公告发布成功: announcementId={}, teamId={}, publisherId={}",
                announcement.getAnnouncementId(),
                teamId,
                publisherId);

        return toTeamAnnouncementDto(announcement, true);
    }

    /**
     * 标记群公告已读。
     *
     * <p>前置条件：{@code userId} 是 {@code teamId} 小队的成员，{@code announcementId} 对小队可见。
     *
     * <p>后置条件：当前用户的已读记录 readAt 已更新。
     *
     * @param teamId         小队 ID
     * @param announcementId 公告 ID
     * @param userId         当前用户 ID
     * @return 更新后的公告
     * @throws BusinessException 非成员或公告不可见时抛出
     */
    public ChatDtos.TeamAnnouncement markAnnouncementRead(String teamId, String announcementId, String userId) {
        if (!teamMemberRepository.findByTeamIdAndUserId(teamId, userId).isPresent()) {
            log.warn("非小队成员尝试标记公告已读: teamId={}, userId={}", teamId, userId);
            throw new BusinessException(TEAM_MEMBER_REQUIRED, "Team membership is required");
        }

        TeamAnnouncement announcement = teamAnnouncementRepository
                .findById(announcementId)
                .orElseThrow(() -> {
                    log.warn("公告不可见: announcementId={}", announcementId);
                    return new BusinessException(
                            ANNOUNCEMENT_NOT_VISIBLE, "Announcement " + announcementId + " is not visible");
                });

        if (!announcement.getTeamId().equals(teamId)) {
            throw new BusinessException(ANNOUNCEMENT_NOT_VISIBLE, "Announcement " + announcementId + " is not visible");
        }

        var read = teamAnnouncementReadRepository.findByAnnouncementIdAndUserId(announcementId, userId);
        if (read.isPresent() && read.get().getReadAt() == null) {
            read.get().setReadAt(Instant.now());
            teamAnnouncementReadRepository.save(read.get());
            log.info("公告已读标记完成: announcementId={}, userId={}", announcementId, userId);
        }

        boolean readByCurrentUser = read.map(r -> r.getReadAt() != null).orElse(false);
        return toTeamAnnouncementDto(announcement, readByCurrentUser);
    }

    // ========================================
    // Team Polls
    // ========================================

    /**
     * 创建群投票，选项至少两个。
     *
     * <p>前置条件：{@code creatorId} 是 {@code teamId} 小队的成员。
     *
     * <p>后置条件：投票和选项已持久化。
     *
     * @param teamId    小队 ID
     * @param creatorId 创建者 ID
     * @param request   投票创建请求
     * @return 已创建的投票
     * @throws BusinessException 非小队成员或选项数量不足时抛出
     */
    public ChatDtos.TeamPoll createPoll(String teamId, String creatorId, ChatDtos.TeamPollCreateRequest request) {
        if (!teamMemberRepository.findByTeamIdAndUserId(teamId, creatorId).isPresent()) {
            log.warn("非小队成员尝试创建投票: teamId={}, userId={}", teamId, creatorId);
            throw new BusinessException(TEAM_MEMBER_REQUIRED, "Team membership is required");
        }

        if (request.getOptions() == null || request.getOptions().size() < 2) {
            throw new BusinessException(POLL_OPTIONS_INVALID, "At least two poll options are required");
        }

        Instant now = Instant.now();
        String pollId = UUID.randomUUID().toString();

        Instant deadline = null;
        if (request.getDeadline() != null) {
            try {
                deadline = Instant.parse(request.getDeadline());
            } catch (Exception e) {
                throw new BusinessException(POLL_UNAVAILABLE, "Invalid deadline format");
            }
        }

        TeamPoll poll = TeamPoll.builder()
                .pollId(pollId)
                .teamId(teamId)
                .title(request.getTitle())
                .deadline(deadline)
                .createdAt(now)
                .build();
        teamPollRepository.save(poll);

        List<ChatDtos.TeamPollOption> optionDtos = new ArrayList<>();
        for (String optionText : request.getOptions()) {
            String optionId = UUID.randomUUID().toString();
            PollOption option = PollOption.builder()
                    .optionId(optionId)
                    .pollId(pollId)
                    .content(optionText)
                    .build();
            pollOptionRepository.save(option);

            ChatDtos.TeamPollOption optDto = new ChatDtos.TeamPollOption();
            optDto.setOptionId(optionId);
            optDto.setContent(optionText);
            optDto.setVoteCount(0);
            optionDtos.add(optDto);
        }

        log.info(
                "群投票创建成功: pollId={}, teamId={}, options={}",
                pollId,
                teamId,
                request.getOptions().size());

        ChatDtos.TeamPoll result = new ChatDtos.TeamPoll();
        result.setPollId(pollId);
        result.setTeamId(teamId);
        result.setTitle(request.getTitle());
        result.setOptions(optionDtos);
        result.setDeadline(request.getDeadline());
        result.setCreatedAt(now.toString());
        return result;
    }

    /**
     * 参与群投票，投票未截止方可投票，同一用户重复投票时覆盖前次选择。
     *
     * <p>前置条件：{@code userId} 是 {@code teamId} 小队的成员，投票未截止。
     *
     * <p>后置条件：投票已记录，返回更新后的投票结果。
     *
     * @param teamId  小队 ID
     * @param pollId  投票 ID
     * @param userId  投票用户 ID
     * @param request 投票请求
     * @return 更新后的投票结果
     * @throws BusinessException 非成员、投票不存在或已截止
     */
    public ChatDtos.TeamPoll votePoll(String teamId, String pollId, String userId, ChatDtos.VotePollRequest request) {
        if (!teamMemberRepository.findByTeamIdAndUserId(teamId, userId).isPresent()) {
            log.warn("非小队成员尝试投票: teamId={}, userId={}", teamId, userId);
            throw new BusinessException(TEAM_MEMBER_REQUIRED, "Team membership is required");
        }

        TeamPoll poll = teamPollRepository.findByPollIdAndTeamId(pollId, teamId).orElseThrow(() -> {
            log.warn("投票不存在: pollId={}, teamId={}", pollId, teamId);
            return new BusinessException(POLL_UNAVAILABLE, "Poll is not available");
        });

        if (poll.getDeadline() != null && Instant.now().isAfter(poll.getDeadline())) {
            log.warn("投票已截止: pollId={}", pollId);
            throw new BusinessException(POLL_UNAVAILABLE, "Poll has ended");
        }

        PollOption targetOption = pollOptionRepository
                .findById(request.getOptionId())
                .orElseThrow(() -> new BusinessException(POLL_UNAVAILABLE, "Poll option not found"));

        if (!targetOption.getPollId().equals(pollId)) {
            throw new BusinessException(POLL_UNAVAILABLE, "Poll option does not belong to this poll");
        }

        Instant now = Instant.now();

        var existingVote = pollVoteRepository.findByPollIdAndUserId(pollId, userId);
        if (existingVote.isPresent()) {
            existingVote.get().setOptionId(request.getOptionId());
            existingVote.get().setVotedAt(now);
            pollVoteRepository.save(existingVote.get());
            log.info("用户修改投票选择: pollId={}, userId={}, newOptionId={}", pollId, userId, request.getOptionId());
        } else {
            PollVote vote = PollVote.builder()
                    .voteId(UUID.randomUUID().toString())
                    .pollId(pollId)
                    .optionId(request.getOptionId())
                    .userId(userId)
                    .votedAt(now)
                    .build();
            pollVoteRepository.save(vote);
            log.info("用户投票成功: pollId={}, userId={}, optionId={}", pollId, userId, request.getOptionId());
        }

        return toTeamPollDto(poll);
    }

    /**
     * 将 TeamPoll 实体转换为 DTO，含各选项票数。
     *
     * <p>前置条件：{@code entity} 已持久化。
     *
     * <p>后置条件：返回包含选项和票数的完整投票 DTO。
     */
    private ChatDtos.TeamPoll toTeamPollDto(TeamPoll entity) {
        List<PollVote> allVotes = pollVoteRepository.findByPollId(entity.getPollId());
        List<PollOption> options = pollOptionRepository.findByPollId(entity.getPollId());
        List<ChatDtos.TeamPollOption> optionDtos = options.stream()
                .map(opt -> {
                    ChatDtos.TeamPollOption dto = new ChatDtos.TeamPollOption();
                    dto.setOptionId(opt.getOptionId());
                    dto.setContent(opt.getContent());
                    long voteCount = allVotes.stream()
                            .filter(v -> v.getOptionId().equals(opt.getOptionId()))
                            .count();
                    dto.setVoteCount((int) voteCount);
                    return dto;
                })
                .collect(Collectors.toList());

        ChatDtos.TeamPoll dto = new ChatDtos.TeamPoll();
        dto.setPollId(entity.getPollId());
        dto.setTeamId(entity.getTeamId());
        dto.setTitle(entity.getTitle());
        dto.setOptions(optionDtos);
        dto.setDeadline(entity.getDeadline() != null ? entity.getDeadline().toString() : null);
        dto.setCreatedAt(entity.getCreatedAt().toString());
        return dto;
    }

    /**
     * 获取会话中除指定用户外的所有成员 ID 列表。
     *
     * <p>前置条件：{@code conversationId} 为有效会话。
     *
     * <p>后置条件：返回不包含 {@code excludeUserId} 的成员 ID 列表。
     */
    private List<String> getRecipientUserIds(String conversationId, String excludeUserId) {
        return conversationMemberRepository.findByConversationId(conversationId).stream()
                .map(io.github.layjason.mayoistar.entity.chat.ConversationMember::getUserId)
                .filter(id -> !id.equals(excludeUserId))
                .collect(Collectors.toList());
    }

    /**
     * 获取会话中所有成员 ID 列表。
     *
     * <p>前置条件：{@code conversationId} 为有效会话。
     *
     * <p>后置条件：返回所有成员 ID 列表。
     */
    private List<String> getAllMemberUserIds(String conversationId) {
        return conversationMemberRepository.findByConversationId(conversationId).stream()
                .map(io.github.layjason.mayoistar.entity.chat.ConversationMember::getUserId)
                .collect(Collectors.toList());
    }

    /**
     * 校验消息中引用的媒体等外部资源是否存在。
     *
     * <p>前置条件：request 已通过 Controller 层的 {@code @ValidMessageContent} 跨字段校验。
     *
     * <p>后置条件：引用的资源均存在时通过，否则抛出异常。
     *
     * <p>不变量：消息内容格式校验由 Controller 层负责，本方法仅校验需要访问数据库的依赖。
     */
    private void validateMessageContent(ChatDtos.SendMessageRequest request) {
        if (request.getKind() == MessageKind.image && !mediaFileRepository.existsById(request.getImageMediaId())) {
            throw new BusinessException(MEDIA_REFERENCE_INVALID, "Media reference is invalid");
        }
    }

    /**
     * 初始化消息的已读状态。发送者标记为已读，其余成员标记为未读。
     *
     * <p>前置条件：消息已持久化，{@code senderId} 属于 {@code conversationId} 会话。
     *
     * <p>后置条件：所有会话成员的 message_reads 记录已创建。
     */
    private void initializeReadStatus(String messageId, String conversationId, String senderId) {
        Instant now = Instant.now();
        List<io.github.layjason.mayoistar.entity.chat.ConversationMember> members =
                conversationMemberRepository.findByConversationId(conversationId);

        for (var member : members) {
            MessageReadStatus status =
                    member.getUserId().equals(senderId) ? MessageReadStatus.read : MessageReadStatus.unread;
            MessageRead mr = MessageRead.builder()
                    .readId(UUID.randomUUID().toString())
                    .messageId(messageId)
                    .userId(member.getUserId())
                    .status(status)
                    .readAt(status == MessageReadStatus.read ? now : null)
                    .build();
            messageReadRepository.save(mr);
        }
    }

    private ChatDtos.ChatMessage toChatMessageDto(ChatMessage entity, MessageReadStatus readStatus) {
        ChatDtos.ChatMessage dto = new ChatDtos.ChatMessage();
        dto.setMessageId(entity.getMessageId());
        dto.setConversationId(entity.getConversationId());
        dto.setSenderId(entity.getSenderId());
        dto.setKind(entity.getKind());
        dto.setRecalled(entity.getRecalled());
        dto.setSentAt(entity.getSentAt().toString());
        dto.setMentionedUserIds(entity.getMentionedUserIds());
        dto.setMentionAll(entity.getMentionAll());
        dto.setReadStatus(readStatus != null ? readStatus.name() : MessageReadStatus.unread.name());

        if (Boolean.TRUE.equals(entity.getRecalled())) {
            dto.setText(null);
            dto.setImage(null);
            dto.setLocation(null);
        } else {
            dto.setText(entity.getText());
            if (entity.getImage() != null) {
                dto.setImage(toMediaFileDto(entity.getImage()));
            }
            if (entity.getLocationLon() != null && entity.getLocationLat() != null) {
                CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
                point.setLongitude(entity.getLocationLon());
                point.setLatitude(entity.getLocationLat());

                CommonDtos.LocationInfo loc = new CommonDtos.LocationInfo();
                loc.setPoint(point);
                loc.setCity(entity.getLocationCity());
                loc.setAddress(entity.getLocationAddress());
                loc.setPlaceName(entity.getLocationPlaceName());
                dto.setLocation(loc);
            }
        }

        return dto;
    }

    private CommonDtos.MediaFile toMediaFileDto(io.github.layjason.mayoistar.entity.common.MediaFile entity) {
        return mediaAccessService.toSignedDto(entity);
    }

    /**
     * 将 TeamAnnouncement 实体转换为 DTO。
     *
     * <p>前置条件：{@code entity} 已持久化。
     *
     * <p>后置条件：返回包含公告所有字段及当前用户已读状态的 DTO。
     *
     * @param entity           公告实体
     * @param readByCurrentUser 当前用户是否已读
     * @return 公告 DTO
     */
    private ChatDtos.TeamAnnouncement toTeamAnnouncementDto(TeamAnnouncement entity, boolean readByCurrentUser) {
        ChatDtos.TeamAnnouncement dto = new ChatDtos.TeamAnnouncement();
        dto.setAnnouncementId(entity.getAnnouncementId());
        dto.setTeamId(entity.getTeamId());
        dto.setContent(entity.getContent());
        dto.setPublisherId(entity.getPublisherId());
        dto.setPublishedAt(entity.getPublishedAt().toString());
        dto.setReadByCurrentUser(readByCurrentUser);
        return dto;
    }
}

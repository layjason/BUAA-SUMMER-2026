package io.github.layjason.mayoistar.service;

import static io.github.layjason.mayoistar.exception.ErrorCodes.CONVERSATION_MEMBER_REQUIRED;
import static io.github.layjason.mayoistar.exception.ErrorCodes.FORWARD_TARGET_UNAVAILABLE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.MESSAGE_CONTENT_INVALID;
import static io.github.layjason.mayoistar.exception.ErrorCodes.MESSAGE_NOT_VISIBLE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.MESSAGE_RECALL_EXPIRED;
import static io.github.layjason.mayoistar.exception.ErrorCodes.MESSAGE_SENDER_REQUIRED;

import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.chat.ChatMessage;
import io.github.layjason.mayoistar.entity.chat.Conversation;
import io.github.layjason.mayoistar.entity.chat.MessageKind;
import io.github.layjason.mayoistar.entity.chat.MessageRead;
import io.github.layjason.mayoistar.entity.chat.MessageReadStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ChatMessageRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.MessageReadRepository;
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
    private final NotificationService notificationService;

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

        chatMessageRepository.save(message);
        log.info(
                "消息已发送: messageId={}, conversationId={}, kind={}",
                message.getMessageId(),
                conversationId,
                request.getKind());

        initializeReadStatus(message.getMessageId(), conversationId, senderId);

        ChatDtos.ChatMessage result = toChatMessageDto(message, MessageReadStatus.read);
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

        List<ChatDtos.ChatMessage> result = new ArrayList<>();

        for (String targetId : targetConversationIds) {
            if (!conversationMemberRepository.existsByConversationIdAndUserId(targetId, senderId)) {
                log.warn("转发目标会话用户非成员: conversationId={}, userId={}", targetId, senderId);
                throw new BusinessException(FORWARD_TARGET_UNAVAILABLE, "Forward target conversation is unavailable");
            }

            ChatMessage forwarded = ChatMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversationId(targetId)
                    .senderId(senderId)
                    .kind(original.getKind())
                    .text(original.getText())
                    .imageMediaId(original.getImageMediaId())
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

            chatMessageRepository.save(forwarded);
            initializeReadStatus(forwarded.getMessageId(), targetId, senderId);
            ChatDtos.ChatMessage forwardedDto = toChatMessageDto(forwarded, MessageReadStatus.read);
            result.add(forwardedDto);
            notificationService.notifyMessageForwarded(forwardedDto, getRecipientUserIds(targetId, senderId));
            log.info("消息已转发: original={}, target={}", originalMessageId, targetId);
        }

        return result;
    }

    // ========================================
    // Private Helpers
    // ========================================

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
     * 校验消息内容与类型匹配。
     *
     * <p>前置条件：request.kind 为有效枚举值。
     *
     * <p>后置条件：内容与类型匹配时通过，否则抛出异常。
     */
    private void validateMessageContent(ChatDtos.SendMessageRequest request) {
        switch (request.getKind()) {
            case text -> {
                if (request.getText() == null || request.getText().isBlank()) {
                    throw new BusinessException(MESSAGE_CONTENT_INVALID, "Message content is invalid for its kind");
                }
            }
            case emoticon -> {
                if ((request.getText() == null || request.getText().isBlank())
                        && (request.getImageMediaId() == null
                                || request.getImageMediaId().isBlank())) {
                    throw new BusinessException(MESSAGE_CONTENT_INVALID, "Message content is invalid for its kind");
                }
            }
            case image -> {
                if (request.getImageMediaId() == null
                        || request.getImageMediaId().isBlank()) {
                    throw new BusinessException(MESSAGE_CONTENT_INVALID, "Message content is invalid for its kind");
                }
            }
            case location -> {
                if (request.getLocation() == null
                        || request.getLocation().getPoint() == null
                        || request.getLocation().getPoint().getLongitude() == null
                        || request.getLocation().getPoint().getLatitude() == null) {
                    throw new BusinessException(MESSAGE_CONTENT_INVALID, "Message content is invalid for its kind");
                }
            }
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

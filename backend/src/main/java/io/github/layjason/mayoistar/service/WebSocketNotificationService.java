package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.chat.MessageReadStatus;
import io.github.layjason.mayoistar.repository.ChatMessageRepository;
import io.github.layjason.mayoistar.repository.MessageReadRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * WebSocket 通知服务，通过 STOMP 向目标用户推送实时事件。
 *
 * <p>类职责：将业务事件转换为 ChatRealtimeEvent 或 FriendRequest 消息，
 * 通过 SimpMessagingTemplate 推送到各用户的私有队列。
 *
 * <p>不变量：非 test profile 时为首选 NotificationService 实现。
 * 每条通知仅推送给指定接收方，不广播给无关用户。
 */
@Component
@RequiredArgsConstructor
@Profile("!test")
@Slf4j
public class WebSocketNotificationService implements NotificationService {

    private static final String CHAT_EVENTS_DEST = "/queue/chat-events";
    private static final String SOCIAL_EVENTS_DEST = "/queue/social-events";
    private static final String AI_EVENTS_DEST = "/queue/ai-events";

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageReadRepository messageReadRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 通知消息创建事件。
     *
     * <p>前置条件：消息已持久化。
     *
     * <p>后置条件：每个接收方收到 ChatRealtimeEvent kind=messageCreated。
     *
     * @param message          已创建的消息
     * @param recipientUserIds 接收方用户 ID 列表
     */
    @Override
    @Transactional(readOnly = true)
    public void notifyMessageCreated(ChatDtos.ChatMessage message, List<String> recipientUserIds) {
        for (String userId : recipientUserIds) {
            int unreadCount = getUnreadCount(userId, message.getConversationId());
            ChatDtos.ChatRealtimeEvent event =
                    buildChatEvent("messageCreated", message.getConversationId(), message, unreadCount);
            sendToUser(userId, CHAT_EVENTS_DEST, event);
        }
        log.info(
                "消息创建通知已推送: messageId={}, conversationId={}, recipients={}",
                message.getMessageId(),
                message.getConversationId(),
                recipientUserIds.size());
    }

    /**
     * 通知消息撤回事件。
     *
     * <p>前置条件：消息 recalled 标记已设置为 true。
     *
     * <p>后置条件：会话中所有成员收到 ChatRealtimeEvent kind=messageRecalled。
     *
     * @param messageId        被撤回消息 ID
     * @param conversationId   所属会话 ID
     * @param recipientUserIds 接收方用户 ID 列表
     */
    @Override
    @Transactional(readOnly = true)
    public void notifyMessageRecalled(String messageId, String conversationId, List<String> recipientUserIds) {
        ChatDtos.ChatMessage recalledMessage = chatMessageRepository
                .findById(messageId)
                .map(entity -> {
                    ChatDtos.ChatMessage dto = new ChatDtos.ChatMessage();
                    dto.setMessageId(entity.getMessageId());
                    dto.setConversationId(entity.getConversationId());
                    dto.setSenderId(entity.getSenderId());
                    dto.setKind(entity.getKind());
                    dto.setRecalled(true);
                    dto.setSentAt(entity.getSentAt().toString());
                    dto.setReadStatus(MessageReadStatus.read.name());
                    return dto;
                })
                .orElseGet(() -> {
                    ChatDtos.ChatMessage dto = new ChatDtos.ChatMessage();
                    dto.setMessageId(messageId);
                    dto.setConversationId(conversationId);
                    dto.setRecalled(true);
                    dto.setReadStatus(MessageReadStatus.read.name());
                    return dto;
                });

        for (String userId : recipientUserIds) {
            ChatDtos.ChatRealtimeEvent event = buildChatEvent("messageRecalled", conversationId, recalledMessage, null);
            sendToUser(userId, CHAT_EVENTS_DEST, event);
        }
        log.info(
                "消息撤回通知已推送: messageId={}, conversationId={}, recipients={}",
                messageId,
                conversationId,
                recipientUserIds.size());
    }

    /**
     * 通知消息转发事件。
     *
     * <p>前置条件：转发消息已在新会话中持久化。
     *
     * <p>后置条件：目标会话成员收到 ChatRealtimeEvent kind=messageForwarded。
     *
     * @param message          转发创建的消息
     * @param recipientUserIds 接收方用户 ID 列表
     */
    @Override
    @Transactional(readOnly = true)
    public void notifyMessageForwarded(ChatDtos.ChatMessage message, List<String> recipientUserIds) {
        for (String userId : recipientUserIds) {
            int unreadCount = getUnreadCount(userId, message.getConversationId());
            ChatDtos.ChatRealtimeEvent event =
                    buildChatEvent("messageForwarded", message.getConversationId(), message, unreadCount);
            sendToUser(userId, CHAT_EVENTS_DEST, event);
        }
        log.info(
                "消息转发通知已推送: messageId={}, conversationId={}, recipients={}",
                message.getMessageId(),
                message.getConversationId(),
                recipientUserIds.size());
    }

    /**
     * 通知好友申请创建事件。
     *
     * <p>前置条件：好友申请已持久化。
     *
     * <p>后置条件：目标用户收到 FriendRequest DTO。
     *
     * @param request 已创建的好友申请
     */
    @Override
    public void notifyFriendRequestCreated(SocialDtos.FriendRequest request) {
        sendToUser(request.getTargetUserId(), SOCIAL_EVENTS_DEST, request);
        log.info("好友申请通知已推送: requestId={}, to={}", request.getRequestId(), request.getTargetUserId());
    }

    /**
     * 通知图片分类任务完成事件。
     *
     * <p>前置条件：分类任务已完成，状态标记为 succeeded 或 failed。
     *
     * <p>后置条件：目标用户通过 /queue/ai-events 收到包含 taskId 和 status 的事件。
     *
     * @param event  分类完成事件
     * @param userId 目标用户 ID
     */
    @Override
    public void notifyImageClassificationCompleted(AiDtos.ImageClassificationCompletedEvent event, String userId) {
        sendToUser(userId, AI_EVENTS_DEST, event);
        log.info("图片分类完成通知已推送: taskId={}, status={}, userId={}", event.getTaskId(), event.getStatus(), userId);
    }

    /**
     * 计算用户在指定会话中的未读消息数。
     *
     * <p>前置条件：userId 为会话成员。
     *
     * <p>后置条件：返回 status=unread 的 message_reads 数量。
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @return 未读消息数
     */
    private int getUnreadCount(String userId, String conversationId) {
        var page = chatMessageRepository.findByConversationId(conversationId);
        List<String> messageIds = page.stream().map(m -> m.getMessageId()).toList();
        if (messageIds.isEmpty()) {
            return 0;
        }
        return (int) messageReadRepository.findByMessageIdInAndUserId(messageIds, userId).stream()
                .filter(mr -> mr.getStatus() == MessageReadStatus.unread)
                .count();
    }

    /**
     * 构建 ChatRealtimeEvent DTO，根据 kind 创建对应的 payload 类型。
     *
     * <p>前置条件：kind 为有效的 ChatRealtimeEventKind 值。
     *
     * <p>后置条件：返回的 event.payload 为与 kind 匹配的具体负载类型。
     *
     * @param kind           事件类型
     * @param conversationId 会话 ID
     * @param message        关联消息（可为 null）
     * @param unreadCount    未读消息数（可为 null，messageRecalled 事件不需要）
     * @return ChatRealtimeEvent
     */
    private ChatDtos.ChatRealtimeEvent buildChatEvent(
            String kind, String conversationId, ChatDtos.ChatMessage message, @Nullable Integer unreadCount) {
        ChatDtos.ChatRealtimeEventPayload payload;
        switch (kind) {
            case "messageCreated":
                ChatDtos.MessageCreatedPayload createdPayload = new ChatDtos.MessageCreatedPayload();
                createdPayload.setMessage(message);
                createdPayload.setConversationUnreadCount(unreadCount != null ? unreadCount : 0);
                payload = createdPayload;
                break;
            case "messageRecalled":
                ChatDtos.MessageRecalledPayload recalledPayload = new ChatDtos.MessageRecalledPayload();
                recalledPayload.setMessage(message);
                payload = recalledPayload;
                break;
            case "messageForwarded":
                ChatDtos.MessageForwardedPayload forwardedPayload = new ChatDtos.MessageForwardedPayload();
                forwardedPayload.setMessage(message);
                forwardedPayload.setConversationUnreadCount(unreadCount != null ? unreadCount : 0);
                payload = forwardedPayload;
                break;
            default:
                throw new IllegalArgumentException("未知事件类型: " + kind);
        }

        ChatDtos.ChatRealtimeEvent event = new ChatDtos.ChatRealtimeEvent();
        event.setKind(kind);
        event.setConversationId(conversationId);
        event.setPayload(payload);
        event.setOccurredAt(Instant.now().toString());
        return event;
    }

    /**
     * 向指定用户的私有队列发送消息。
     *
     * <p>前置条件：用户已通过 STOMP CONNECT 鉴权。
     *
     * <p>后置条件：消息被投递到 /user/{userId}{destination}。
     *
     * @param userId      目标用户 ID
     * @param destination 目标队列（如 /queue/chat-events）
     * @param payload     消息负载
     */
    private void sendToUser(String userId, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(userId, destination, payload);
    }
}

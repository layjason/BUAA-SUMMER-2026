package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * No-Op 通知服务，记录日志但不实际发送通知。
 *
 * <p>类职责：在 WebSocket 基础设施就绪前，提供无副作用的通知服务默认实现。
 *
 * <p>后置条件：所有通知调用均被记录到日志，不抛出异常。
 */
@Component
@Slf4j
public class NoOpNotificationService implements NotificationService {

    @Override
    public void notifyMessageCreated(ChatDtos.ChatMessage message, List<String> recipientUserIds) {
        log.info(
                "消息创建通知（No-Op）: messageId={}, conversationId={}, recipients={}",
                message.getMessageId(),
                message.getConversationId(),
                recipientUserIds);
    }

    @Override
    public void notifyMessageRecalled(String messageId, String conversationId, List<String> recipientUserIds) {
        log.info(
                "消息撤回通知（No-Op）: messageId={}, conversationId={}, recipients={}",
                messageId,
                conversationId,
                recipientUserIds);
    }

    @Override
    public void notifyMessageForwarded(ChatDtos.ChatMessage message, List<String> recipientUserIds) {
        log.info(
                "消息转发通知（No-Op）: messageId={}, conversationId={}, recipients={}",
                message.getMessageId(),
                message.getConversationId(),
                recipientUserIds);
    }

    @Override
    public void notifyFriendRequestCreated(SocialDtos.FriendRequest request) {
        log.info(
                "好友申请通知（No-Op）: requestId={}, from={}, to={}",
                request.getRequestId(),
                request.getRequesterId(),
                request.getTargetUserId());
    }
}

package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 测试用通知服务，记录所有通知调用以供断言。
 *
 * <p>类职责：在 test profile 下作为 NotificationService 的 @Primary 实现，
 * 拦截通知调用并保存参数。
 */
@Component
@Primary
@Profile("test")
@Slf4j
public class CapturingNotificationService implements NotificationService {

    @Getter
    private final List<ChatDtos.ChatMessage> createdMessages = new ArrayList<>();

    @Getter
    private final List<CapturedRecall> recalls = new ArrayList<>();

    @Getter
    private final List<ChatDtos.ChatMessage> forwardedMessages = new ArrayList<>();

    @Getter
    private final List<SocialDtos.FriendRequest> friendRequests = new ArrayList<>();

    @Getter
    private final List<CapturedAiEvent> aiEvents = new ArrayList<>();

    @Getter
    private final List<CapturedPeerRead> peerReads = new ArrayList<>();

    @Override
    public void notifyMessageCreated(ChatDtos.ChatMessage message, List<String> recipientUserIds) {
        log.info("测试通知: messageCreated messageId={}, recipients={}", message.getMessageId(), recipientUserIds);
        createdMessages.add(message);
    }

    @Override
    public void notifyMessageRecalled(String messageId, String conversationId, List<String> recipientUserIds) {
        log.info("测试通知: messageRecalled messageId={}, recipients={}", messageId, recipientUserIds);
        recalls.add(new CapturedRecall(messageId, conversationId, recipientUserIds));
    }

    @Override
    public void notifyMessageForwarded(ChatDtos.ChatMessage message, List<String> recipientUserIds) {
        log.info("测试通知: messageForwarded messageId={}, recipients={}", message.getMessageId(), recipientUserIds);
        forwardedMessages.add(message);
    }

    @Override
    public void notifyFriendRequestCreated(SocialDtos.FriendRequest request) {
        log.info("测试通知: friendRequestCreated requestId={}", request.getRequestId());
        friendRequests.add(request);
    }

    @Override
    public void notifyImageClassificationCompleted(AiDtos.ImageClassificationCompletedEvent event, String userId) {
        log.info("测试通知: imageClassificationCompleted taskId={}, status={}", event.getTaskId(), event.getStatus());
        aiEvents.add(new CapturedAiEvent(event, userId));
    }

    @Override
    public void notifyMessagePeerRead(String conversationId, String messageId, String senderUserId) {
        log.info("测试通知: messagePeerRead messageId={}, to={}", messageId, senderUserId);
        peerReads.add(new CapturedPeerRead(conversationId, messageId, senderUserId));
    }

    public record CapturedRecall(String messageId, String conversationId, List<String> recipientUserIds) {}

    public record CapturedAiEvent(AiDtos.ImageClassificationCompletedEvent event, String userId) {}

    public record CapturedPeerRead(String conversationId, String messageId, String senderUserId) {}
}

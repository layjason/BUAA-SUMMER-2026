package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.chat.ChatMessage;
import io.github.layjason.mayoistar.entity.chat.MessageKind;
import io.github.layjason.mayoistar.entity.chat.MessageReadStatus;
import io.github.layjason.mayoistar.repository.ChatMessageRepository;
import io.github.layjason.mayoistar.repository.MessageReadRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class WebSocketNotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageReadRepository messageReadRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private WebSocketNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService =
                new WebSocketNotificationService(messagingTemplate, messageReadRepository, chatMessageRepository);
    }

    @Test
    @DisplayName("消息创建通知推送到所有接收方")
    void notifyMessageCreatedSendsToAllRecipients() {
        ChatDtos.ChatMessage message = createChatMessage();
        List<String> recipientUserIds = List.of("user-a", "user-b");
        ChatMessage entity = createChatMessageEntity();

        when(chatMessageRepository.findByConversationId("conv-1")).thenReturn(List.of(entity));
        when(messageReadRepository.findByMessageIdInAndUserId(eq(List.of("msg-1")), eq("user-a")))
                .thenReturn(List.of());
        when(messageReadRepository.findByMessageIdInAndUserId(eq(List.of("msg-1")), eq("user-b")))
                .thenReturn(List.of());

        notificationService.notifyMessageCreated(message, recipientUserIds);

        ArgumentCaptor<ChatDtos.ChatRealtimeEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatDtos.ChatRealtimeEvent.class);
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(any(), eq("/queue/chat-events"), eventCaptor.capture());

        List<ChatDtos.ChatRealtimeEvent> events = eventCaptor.getAllValues();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getKind()).isEqualTo("messageCreated");
        assertThat(events.get(0).getConversationId()).isEqualTo("conv-1");
        assertThat(events.get(0).getPayload()).isInstanceOf(ChatDtos.MessageCreatedPayload.class);
        ChatDtos.MessageCreatedPayload payload =
                (ChatDtos.MessageCreatedPayload) events.get(0).getPayload();
        assertThat(payload.getMessage().getMessageId()).isEqualTo("msg-1");
        assertThat(payload.getConversationUnreadCount()).isZero();
    }

    @Test
    @DisplayName("消息撤回通知推送到所有接收方")
    void notifyMessageRecalledSendsRecallEvents() {
        ChatMessage entity = createChatMessageEntity();
        List<String> recipientUserIds = List.of("user-a");

        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(entity));

        notificationService.notifyMessageRecalled("msg-1", "conv-1", recipientUserIds);

        ArgumentCaptor<ChatDtos.ChatRealtimeEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatDtos.ChatRealtimeEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq("user-a"), eq("/queue/chat-events"), eventCaptor.capture());

        ChatDtos.ChatRealtimeEvent event = eventCaptor.getValue();
        assertThat(event.getKind()).isEqualTo("messageRecalled");
        assertThat(event.getPayload()).isInstanceOf(ChatDtos.MessageRecalledPayload.class);
        ChatDtos.MessageRecalledPayload payload = (ChatDtos.MessageRecalledPayload) event.getPayload();
        assertThat(payload.getMessage().getRecalled()).isTrue();
    }

    @Test
    @DisplayName("消息转发通知推送到所有接收方")
    void notifyMessageForwardedSendsToAllRecipients() {
        ChatDtos.ChatMessage message = createChatMessage();
        List<String> recipientUserIds = List.of("user-a");
        ChatMessage entity = createChatMessageEntity();

        when(chatMessageRepository.findByConversationId("conv-1")).thenReturn(List.of(entity));
        when(messageReadRepository.findByMessageIdInAndUserId(eq(List.of("msg-1")), eq("user-a")))
                .thenReturn(List.of());

        notificationService.notifyMessageForwarded(message, recipientUserIds);

        ArgumentCaptor<ChatDtos.ChatRealtimeEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatDtos.ChatRealtimeEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq("user-a"), eq("/queue/chat-events"), eventCaptor.capture());

        ChatDtos.ChatRealtimeEvent event = eventCaptor.getValue();
        assertThat(event.getKind()).isEqualTo("messageForwarded");
        assertThat(event.getPayload()).isInstanceOf(ChatDtos.MessageForwardedPayload.class);
    }

    @Test
    @DisplayName("好友申请通知推送到目标用户")
    void notifyFriendRequestCreatedSendsToTargetUser() {
        SocialDtos.FriendRequest request = new SocialDtos.FriendRequest();
        request.setRequestId("req-1");
        request.setRequesterId("user-a");
        request.setTargetUserId("user-b");

        notificationService.notifyFriendRequestCreated(request);

        verify(messagingTemplate).convertAndSendToUser(eq("user-b"), eq("/queue/social-events"), eq(request));
    }

    @Test
    @DisplayName("撤回时消息不存在仍推送最小事件")
    void notifyMessageRecalledWhenMessage404() {
        List<String> recipientUserIds = List.of("user-a");
        when(chatMessageRepository.findById("msg-404")).thenReturn(Optional.empty());

        notificationService.notifyMessageRecalled("msg-404", "conv-1", recipientUserIds);

        ArgumentCaptor<ChatDtos.ChatRealtimeEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatDtos.ChatRealtimeEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq("user-a"), eq("/queue/chat-events"), eventCaptor.capture());

        ChatDtos.ChatRealtimeEvent event = eventCaptor.getValue();
        assertThat(event.getKind()).isEqualTo("messageRecalled");
        assertThat(event.getPayload()).isInstanceOf(ChatDtos.MessageRecalledPayload.class);
        ChatDtos.MessageRecalledPayload payload = (ChatDtos.MessageRecalledPayload) event.getPayload();
        assertThat(payload.getMessage().getMessageId()).isEqualTo("msg-404");
        assertThat(payload.getMessage().getRecalled()).isTrue();
    }

    private ChatDtos.ChatMessage createChatMessage() {
        ChatDtos.ChatMessage message = new ChatDtos.ChatMessage();
        message.setMessageId("msg-1");
        message.setConversationId("conv-1");
        message.setSenderId("sender-1");
        message.setKind(MessageKind.text);
        message.setText("Hello");
        message.setReadStatus(MessageReadStatus.read.name());
        message.setRecalled(false);
        message.setSentAt(Instant.now().toString());
        return message;
    }

    private ChatMessage createChatMessageEntity() {
        ChatMessage entity = new ChatMessage();
        entity.setMessageId("msg-1");
        entity.setConversationId("conv-1");
        entity.setSenderId("sender-1");
        entity.setKind(MessageKind.text);
        entity.setText("Hello");
        entity.setSentAt(Instant.now());
        entity.setRecalled(false);
        return entity;
    }
}

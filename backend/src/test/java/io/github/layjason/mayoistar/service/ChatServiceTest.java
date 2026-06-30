package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.chat.ChatMessage;
import io.github.layjason.mayoistar.entity.chat.Conversation;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.chat.ConversationMember;
import io.github.layjason.mayoistar.entity.chat.MessageKind;
import io.github.layjason.mayoistar.entity.chat.MessageReadStatus;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ChatMessageRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.MessageReadRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private MessageReadRepository messageReadRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User tomori;
    private User anon;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        tomori = createUser("tomori@mygo.test", "燈");
        anon = createUser("anon@mygo.test", "愛音");

        conversation = Conversation.builder()
                .conversationId(UUID.randomUUID().toString())
                .kind(ConversationKind.friend)
                .title("燈 & 愛音")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        conversationRepository.save(conversation);

        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(conversation.getConversationId())
                .userId(tomori.getUserId())
                .joinedAt(Instant.now())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(conversation.getConversationId())
                .userId(anon.getUserId())
                .joinedAt(Instant.now())
                .build());
    }

    // ========================================
    // sendMessage Tests
    // ========================================

    @Test
    @DisplayName("发送文字消息 - 成功创建消息并初始化已读状态")
    void sendMessage_text() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello Bob!");

        ChatDtos.ChatMessage result =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        assertThat(result.getKind()).isEqualTo(MessageKind.text);
        assertThat(result.getText()).isEqualTo("Hello Bob!");
        assertThat(result.getSenderId()).isEqualTo(tomori.getUserId());
        assertThat(result.getRecalled()).isFalse();
        assertThat(result.getReadStatus()).isEqualTo("read");

        assertThat(messageReadRepository.findByMessageIdInAndUserId(List.of(result.getMessageId()), tomori.getUserId()))
                .allMatch(mr -> mr.getStatus() == MessageReadStatus.read);
        assertThat(messageReadRepository.findByMessageIdInAndUserId(List.of(result.getMessageId()), anon.getUserId()))
                .allMatch(mr -> mr.getStatus() == MessageReadStatus.unread);

        assertThat(capturingNotification().getCreatedMessages()).isNotEmpty();
        assertThat(capturingNotification().getCreatedMessages().stream()
                        .anyMatch(m -> m.getMessageId().equals(result.getMessageId())))
                .isTrue();
    }

    private CapturingNotificationService capturingNotification() {
        return (CapturingNotificationService) notificationService;
    }

    @Test
    @DisplayName("发送文字消息 - 空文本抛 MESSAGE_CONTENT_INVALID")
    void sendMessage_emptyText() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("");

        assertThatThrownBy(() -> chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("content is invalid");
    }

    @Test
    @DisplayName("发送图片消息 - 成功创建消息并初始化已读状态")
    void sendMessage_image() {
        String mediaId = UUID.randomUUID().toString();
        MediaFile mediaFile = MediaFile.builder()
                .mediaId(mediaId)
                .fileName("test.png")
                .contentType("image/png")
                .sizeBytes(1024L)
                .usage(MediaUsage.chatImage)
                .storagePath("/test/test.png")
                .uploadedBy(tomori.getUserId())
                .uploadedAt(Instant.now())
                .build();
        entityManager.persist(mediaFile);
        entityManager.flush();

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.image);
        request.setImageMediaId(mediaId);

        ChatDtos.ChatMessage result =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        assertThat(result.getKind()).isEqualTo(MessageKind.image);
        assertThat(result.getSenderId()).isEqualTo(tomori.getUserId());
        assertThat(result.getRecalled()).isFalse();
        assertThat(result.getReadStatus()).isEqualTo("read");
        assertThat(messageReadRepository.findByMessageIdInAndUserId(List.of(result.getMessageId()), anon.getUserId()))
                .allMatch(mr -> mr.getStatus() == MessageReadStatus.unread);
    }

    @Test
    @DisplayName("发送图片消息 - 空 mediaId 抛 MESSAGE_CONTENT_INVALID")
    void sendMessage_imageNoMediaId() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.image);

        assertThatThrownBy(() -> chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("content is invalid");
    }

    @Test
    @DisplayName("发送位置消息 - 成功创建")
    void sendMessage_location() {
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(116.4074);
        point.setLatitude(39.9042);
        CommonDtos.LocationInfo location = new CommonDtos.LocationInfo();
        location.setPoint(point);
        location.setCity("北京");
        location.setAddress("天安门广场");
        location.setPlaceName("天安门");

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.location);
        request.setLocation(location);

        ChatDtos.ChatMessage result =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        assertThat(result.getKind()).isEqualTo(MessageKind.location);
        assertThat(result.getSenderId()).isEqualTo(tomori.getUserId());
        assertThat(result.getRecalled()).isFalse();
        assertThat(result.getLocation()).isNotNull();
        assertThat(result.getLocation().getPoint().getLongitude()).isEqualTo(116.4074);
        assertThat(result.getLocation().getPoint().getLatitude()).isEqualTo(39.9042);
        assertThat(result.getLocation().getCity()).isEqualTo("北京");
        assertThat(result.getLocation().getAddress()).isEqualTo("天安门广场");
        assertThat(result.getLocation().getPlaceName()).isEqualTo("天安门");
    }

    @Test
    @DisplayName("发送位置消息 - 缺坐标抛 MESSAGE_CONTENT_INVALID")
    void sendMessage_locationNoPoint() {
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLatitude(39.9042);
        CommonDtos.LocationInfo location = new CommonDtos.LocationInfo();
        location.setPoint(point);
        location.setCity("北京");

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.location);
        request.setLocation(location);

        assertThatThrownBy(() -> chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("content is invalid");
    }

    @Test
    @DisplayName("发送位置消息 - 无 location 抛 MESSAGE_CONTENT_INVALID")
    void sendMessage_locationNull() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.location);

        assertThatThrownBy(() -> chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("content is invalid");
    }

    @Test
    @DisplayName("发送消息 - 非会话成员抛 CONVERSATION_MEMBER_REQUIRED")
    void sendMessage_notMember() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hi");

        assertThatThrownBy(() -> chatService.sendMessage(conversation.getConversationId(), "stranger", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conversation membership");
    }

    @Test
    @DisplayName("发送表情消息 - unicode表情文本成功")
    void sendMessage_emoticon_text() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.emoticon);
        request.setText("😀");

        ChatDtos.ChatMessage result =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        assertThat(result.getKind()).isEqualTo(MessageKind.emoticon);
        assertThat(result.getText()).isEqualTo("😀");
        assertThat(result.getRecalled()).isFalse();
    }

    @Test
    @DisplayName("发送表情消息 - 无内容抛 MESSAGE_CONTENT_INVALID")
    void sendMessage_emoticon_noContent() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.emoticon);

        assertThatThrownBy(() -> chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("content is invalid");
    }

    // ========================================
    // listMessages Tests
    // ========================================

    @Test
    @DisplayName("获取消息列表 - 返回消息及已读状态")
    void listMessages_success() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello!");
        chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        var result = chatService.listMessages(conversation.getConversationId(), anon.getUserId(), 1, 20);

        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getItems().getFirst().getReadStatus()).isEqualTo("unread");
    }

    // ========================================
    // markMessagesRead Tests
    // ========================================

    @Test
    @DisplayName("标记已读 - 成功更新已读状态")
    void markMessagesRead_success() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello!");
        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        List<ChatDtos.ChatMessage> result =
                chatService.markMessagesRead(anon.getUserId(), List.of(sent.getMessageId()));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getReadStatus()).isEqualTo("read");
    }

    @Test
    @DisplayName("标记已读 - 空列表返回空")
    void markMessagesRead_empty() {
        List<ChatDtos.ChatMessage> result = chatService.markMessagesRead(anon.getUserId(), List.of());
        assertThat(result).isEmpty();
    }

    // ========================================
    // recallMessage Tests
    // ========================================

    @Test
    @DisplayName("撤回消息 - 发送者2分钟内成功撤回")
    void recallMessage_success() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Mistake");
        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        ChatDtos.ChatMessage result = chatService.recallMessage(sent.getMessageId(), tomori.getUserId());

        assertThat(result.getRecalled()).isTrue();
        assertThat(result.getText()).isNull();

        assertThat(capturingNotification().getRecalls()).isNotEmpty();
        assertThat(capturingNotification().getRecalls().stream()
                        .anyMatch(r -> r.messageId().equals(sent.getMessageId())))
                .isTrue();
    }

    @Test
    @DisplayName("撤回消息 - 非发送者抛 MESSAGE_SENDER_REQUIRED")
    void recallMessage_notSender() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello");
        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        assertThatThrownBy(() -> chatService.recallMessage(sent.getMessageId(), anon.getUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sender permission");
    }

    @Test
    @DisplayName("撤回消息 - 超时抛 MESSAGE_RECALL_EXPIRED")
    void recallMessage_expired() {
        ChatMessage oldMessage = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(conversation.getConversationId())
                .senderId(tomori.getUserId())
                .kind(MessageKind.text)
                .text("Old message")
                .recalled(false)
                .sentAt(Instant.now().minus(3, ChronoUnit.MINUTES))
                .build();
        chatMessageRepository.save(oldMessage);

        assertThatThrownBy(() -> chatService.recallMessage(oldMessage.getMessageId(), tomori.getUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recall window");
    }

    // ========================================
    // forwardMessage Tests
    // ========================================

    @Test
    @DisplayName("转发消息 - 成功转发到目标会话")
    void forwardMessage_success() {
        Conversation targetConv = Conversation.builder()
                .conversationId(UUID.randomUUID().toString())
                .kind(ConversationKind.friend)
                .title("燈 & 楽奈")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        conversationRepository.save(targetConv);
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(targetConv.getConversationId())
                .userId(tomori.getUserId())
                .joinedAt(Instant.now())
                .build());

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Forward me");
        ChatDtos.ChatMessage original =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        List<ChatDtos.ChatMessage> result = chatService.forwardMessage(
                original.getMessageId(), tomori.getUserId(), List.of(targetConv.getConversationId()));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getConversationId()).isEqualTo(targetConv.getConversationId());
        assertThat(result.getFirst().getText()).isEqualTo("Forward me");

        assertThat(capturingNotification().getForwardedMessages()).isNotEmpty();
        assertThat(capturingNotification().getForwardedMessages().stream()
                        .anyMatch(m -> m.getConversationId().equals(targetConv.getConversationId())))
                .isTrue();
    }

    @Test
    @DisplayName("转发消息 - 目标会话非成员抛 FORWARD_TARGET_UNAVAILABLE")
    void forwardMessage_notMemberInTarget() {
        Conversation targetConv = Conversation.builder()
                .conversationId(UUID.randomUUID().toString())
                .kind(ConversationKind.friend)
                .title("燈 & 楽奈 Only")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        conversationRepository.save(targetConv);
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(targetConv.getConversationId())
                .userId(tomori.getUserId())
                .joinedAt(Instant.now())
                .build());

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hi");
        ChatDtos.ChatMessage original =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        assertThatThrownBy(() -> chatService.forwardMessage(
                        original.getMessageId(), anon.getUserId(), List.of(targetConv.getConversationId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("target conversation");
    }

    @Test
    @DisplayName("转发消息 - 已撤回消息仍可转发为正常消息")
    void forwardMessage_recalledMessage() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Secret");
        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        chatService.recallMessage(sent.getMessageId(), tomori.getUserId());

        List<ChatDtos.ChatMessage> result = chatService.forwardMessage(
                sent.getMessageId(), tomori.getUserId(), List.of(conversation.getConversationId()));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getRecalled()).isFalse();
        assertThat(result.getFirst().getText()).isEqualTo("Secret");
    }

    // ========================================
    // Helpers
    // ========================================

    private User createUser(String email, String nickname) {
        User user = User.builder()
                .userId(UUID.randomUUID().toString())
                .email(email)
                .nickname(nickname)
                .passwordHash("hash")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return userRepository.save(user);
    }
}

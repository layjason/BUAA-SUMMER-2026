package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.validation.MessageContentValidator;
import io.github.layjason.mayoistar.entity.chat.ChatMessage;
import io.github.layjason.mayoistar.entity.chat.Conversation;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.chat.ConversationMember;
import io.github.layjason.mayoistar.entity.chat.MessageKind;
import io.github.layjason.mayoistar.entity.chat.MessageReadStatus;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Team;
import io.github.layjason.mayoistar.entity.social.TeamJoinMode;
import io.github.layjason.mayoistar.entity.social.TeamMember;
import io.github.layjason.mayoistar.entity.social.TeamMemberRole;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ChatMessageRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.MessageReadRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
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
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ChatServiceTest extends AbstractIntegrationTest {

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

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

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
    @DisplayName("发送文字消息 - 空文本抛 MESSAGE_CONTENT_INVALID（Validator 层校验）")
    void sendMessage_emptyText() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("");

        MessageContentValidator validator = new MessageContentValidator();
        assertThatThrownBy(() -> validator.isValid(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("content is invalid");
    }

    @Test
    @DisplayName("发送图片消息 - 成功创建消息并初始化已读状态，访问策略更新为 conversationMember")
    void sendMessage_image() {
        UUID mediaId = UUID.randomUUID();
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
        assertThat(result.getImage()).isNotNull();
        assertThat(result.getImage().getSignedUrl()).contains("policy=conversationMember");
        assertThat(result.getImage().getSignedUrl()).contains("scope=" + conversation.getConversationId());
        assertThat(messageReadRepository.findByMessageIdInAndUserId(List.of(result.getMessageId()), anon.getUserId()))
                .allMatch(mr -> mr.getStatus() == MessageReadStatus.unread);

        // 验证 accessPolicy 已从默认 owner 更新为 conversationMember
        entityManager.flush();
        entityManager.clear();

        var updatedMediaFile = mediaFileRepository.findById(mediaId).orElseThrow();
        assertThat(updatedMediaFile.getAccessPolicy()).isEqualTo(MediaAccessPolicy.conversationMember);
        assertThat(updatedMediaFile.getAccessScopeId()).isEqualTo(conversation.getConversationId());

        // 通过 listMessages 验证消息中签出的 URL 使用 conversationMember 策略
        var messagesPage = chatService.listMessages(conversation.getConversationId(), tomori.getUserId(), 1, 20);
        var sentMessageDto = messagesPage.getItems().stream()
                .filter(m -> m.getMessageId().equals(result.getMessageId()))
                .findFirst()
                .orElseThrow();
        assertThat(sentMessageDto.getImage()).isNotNull();
        assertThat(sentMessageDto.getImage().getSignedUrl()).contains("policy=conversationMember");
    }

    @Test
    @DisplayName("发送图片消息 - 空 mediaId 抛 MESSAGE_CONTENT_INVALID（Validator 层校验）")
    void sendMessage_imageNoMediaId() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.image);

        MessageContentValidator validator = new MessageContentValidator();
        assertThatThrownBy(() -> validator.isValid(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("content is invalid");
    }

    @Test
    @DisplayName("发送图片消息 - 不存在的 mediaId 抛 MEDIA_REFERENCE_INVALID")
    void sendMessage_imageNonExistentMediaId() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.image);
        request.setImageMediaId(UUID.randomUUID());

        assertThatThrownBy(() -> chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Media reference is invalid");
    }

    @Test
    @DisplayName("发送图片消息 - 发送者不是图片上传者应抛 MEDIA_REFERENCE_INVALID")
    void sendMessage_imageNotOwnedBySender() {
        UUID mediaId = UUID.randomUUID();
        MediaFile mediaFile = MediaFile.builder()
                .mediaId(mediaId)
                .fileName("other.png")
                .contentType("image/png")
                .sizeBytes(1024L)
                .usage(MediaUsage.chatImage)
                .storagePath("/test/other.png")
                .uploadedBy(anon.getUserId())
                .uploadedAt(Instant.now())
                .build();
        entityManager.persist(mediaFile);
        entityManager.flush();

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.image);
        request.setImageMediaId(mediaId);

        assertThatThrownBy(() -> chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Media reference is invalid");
    }

    @Test
    @DisplayName("发送图片消息 - 引用已删除的图片应抛 MEDIA_REFERENCE_INVALID")
    void sendMessage_imageDeletedThrows() {
        UUID mediaId = UUID.randomUUID();
        MediaFile mediaFile = MediaFile.builder()
                .mediaId(mediaId)
                .fileName("deleted.png")
                .contentType("image/png")
                .sizeBytes(1024L)
                .usage(MediaUsage.chatImage)
                .storagePath("/test/deleted.png")
                .uploadedBy(tomori.getUserId())
                .uploadedAt(Instant.now())
                .deletedAt(Instant.now())
                .build();
        entityManager.persist(mediaFile);
        entityManager.flush();

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.image);
        request.setImageMediaId(mediaId);

        assertThatThrownBy(() -> chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Media reference is invalid");
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
    @DisplayName("发送位置消息 - 缺坐标抛 MESSAGE_CONTENT_INVALID（Validator 层校验）")
    void sendMessage_locationNoPoint() {
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLatitude(39.9042);
        CommonDtos.LocationInfo location = new CommonDtos.LocationInfo();
        location.setPoint(point);
        location.setCity("北京");

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.location);
        request.setLocation(location);

        MessageContentValidator validator = new MessageContentValidator();
        assertThatThrownBy(() -> validator.isValid(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("content is invalid");
    }

    @Test
    @DisplayName("发送位置消息 - 无 location 抛 MESSAGE_CONTENT_INVALID（Validator 层校验）")
    void sendMessage_locationNull() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.location);

        MessageContentValidator validator = new MessageContentValidator();
        assertThatThrownBy(() -> validator.isValid(request, null))
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
    @DisplayName("发送消息 - friend 会话中 member 使用 mentionAll 不抛错")
    void sendMessage_mentionAllInFriendConversation() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("@all hello");
        request.setMentionAll(true);

        ChatDtos.ChatMessage result =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        assertThat(result.getMentionAll()).isTrue();
    }

    @Test
    @DisplayName("发送消息 - team 会话中普通成员使用 mentionAll 抛 MESSAGE_CONTENT_INVALID")
    void sendMessage_mentionAllByRegularMemberInTeam_throws() {
        String teamConversationId = UUID.randomUUID().toString();
        Conversation teamConv = Conversation.builder()
                .conversationId(teamConversationId)
                .kind(ConversationKind.team)
                .title("测试小队群聊")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        conversationRepository.save(teamConv);

        Team team = Team.builder()
                .teamId(UUID.randomUUID().toString())
                .name("mentionAll小队")
                .tags(List.of())
                .joinMode(TeamJoinMode.publicJoin)
                .capacity(10)
                .status(TeamStatus.active)
                .creatorId(tomori.getUserId())
                .leaderId(tomori.getUserId())
                .chatId(teamConversationId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        teamRepository.save(team);

        teamMemberRepository.save(TeamMember.builder()
                .memberId(UUID.randomUUID().toString())
                .teamId(team.getTeamId())
                .userId(tomori.getUserId())
                .role(TeamMemberRole.leader)
                .points(0)
                .joinedAt(Instant.now())
                .build());
        teamMemberRepository.save(TeamMember.builder()
                .memberId(UUID.randomUUID().toString())
                .teamId(team.getTeamId())
                .userId(anon.getUserId())
                .role(TeamMemberRole.member)
                .points(0)
                .joinedAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(teamConversationId)
                .userId(anon.getUserId())
                .joinedAt(Instant.now())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(teamConversationId)
                .userId(tomori.getUserId())
                .joinedAt(Instant.now())
                .build());

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("@all 公告");
        request.setMentionAll(true);

        assertThatThrownBy(() -> chatService.sendMessage(teamConversationId, anon.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mentionAll");
    }

    @Test
    @DisplayName("发送消息 - team 会话中队长使用 mentionAll 成功")
    void sendMessage_mentionAllByLeaderInTeam_succeeds() {
        String teamConversationId = UUID.randomUUID().toString();
        Conversation teamConv = Conversation.builder()
                .conversationId(teamConversationId)
                .kind(ConversationKind.team)
                .title("测试小队群聊")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        conversationRepository.save(teamConv);

        Team team = Team.builder()
                .teamId(UUID.randomUUID().toString())
                .name("mentionAll队长小队")
                .tags(List.of())
                .joinMode(TeamJoinMode.publicJoin)
                .capacity(10)
                .status(TeamStatus.active)
                .creatorId(tomori.getUserId())
                .leaderId(tomori.getUserId())
                .chatId(teamConversationId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        teamRepository.save(team);

        teamMemberRepository.save(TeamMember.builder()
                .memberId(UUID.randomUUID().toString())
                .teamId(team.getTeamId())
                .userId(tomori.getUserId())
                .role(TeamMemberRole.leader)
                .points(0)
                .joinedAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(teamConversationId)
                .userId(tomori.getUserId())
                .joinedAt(Instant.now())
                .build());

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("@all 重要通知");
        request.setMentionAll(true);

        ChatDtos.ChatMessage result = chatService.sendMessage(teamConversationId, tomori.getUserId(), request);

        assertThat(result.getMentionAll()).isTrue();
        assertThat(result.getText()).isEqualTo("@all 重要通知");
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

    @Test
    @DisplayName("标记已读 - 图片消息标记已读不触发懒加载异常")
    void markMessagesRead_imageMessage() {
        UUID mediaId = UUID.randomUUID();
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

        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        entityManager.flush();
        entityManager.clear();

        List<ChatDtos.ChatMessage> result =
                chatService.markMessagesRead(anon.getUserId(), List.of(sent.getMessageId()));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getReadStatus()).isEqualTo("read");
        assertThat(result.getFirst().getImage()).isNotNull();
    }

    @Test
    @DisplayName("发送消息 - 单聊中返回peerReadStatus=unread")
    void sendMessage_peerReadStatus_unread() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello!");
        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        assertThat(sent.getPeerReadStatus()).isEqualTo("unread");
    }

    @Test
    @DisplayName("获取消息列表 - 发送方能看到自己的peerReadStatus=unread")
    void listMessages_peerReadStatus_unread() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello!");
        chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        var result = chatService.listMessages(conversation.getConversationId(), tomori.getUserId(), 1, 20);

        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getItems().getFirst().getSenderId()).isEqualTo(tomori.getUserId());
        assertThat(result.getItems().getFirst().getPeerReadStatus()).isEqualTo("unread");
    }

    @Test
    @DisplayName("获取消息列表 - 接收方消息不返回peerReadStatus")
    void listMessages_receiverNoPeerReadStatus() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello!");
        chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        var result = chatService.listMessages(conversation.getConversationId(), anon.getUserId(), 1, 20);

        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getItems().getFirst().getPeerReadStatus()).isNull();
    }

    @Test
    @DisplayName("标记已读 - 单聊触发peerRead通知")
    void markMessagesRead_triggersPeerReadNotification() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello!");
        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        chatService.markMessagesRead(anon.getUserId(), List.of(sent.getMessageId()));

        assertThat(capturingNotification().getPeerReads()).isNotEmpty();
        assertThat(capturingNotification().getPeerReads().stream()
                        .anyMatch(r -> r.messageId().equals(sent.getMessageId())
                                && r.senderUserId().equals(tomori.getUserId())))
                .isTrue();
    }

    @Test
    @DisplayName("标记已读后发送方listMessages能看见peerReadStatus=read")
    void listMessages_peerReadStatus_afterRead() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello!");
        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        chatService.markMessagesRead(anon.getUserId(), List.of(sent.getMessageId()));

        var result = chatService.listMessages(conversation.getConversationId(), tomori.getUserId(), 1, 20);

        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getItems().getFirst().getSenderId()).isEqualTo(tomori.getUserId());
        assertThat(result.getItems().getFirst().getPeerReadStatus()).isEqualTo("read");
    }

    @Test
    @DisplayName("群聊发送消息不返回peerReadStatus")
    void sendMessage_teamChat_noPeerReadStatus() {
        User sakiko = createUser("sakiko@mygo.test", "祥子");
        Conversation teamConv = Conversation.builder()
                .conversationId(UUID.randomUUID().toString())
                .kind(ConversationKind.team)
                .title("Ave Mujica")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        conversationRepository.save(teamConv);
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(teamConv.getConversationId())
                .userId(tomori.getUserId())
                .joinedAt(Instant.now())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(teamConv.getConversationId())
                .userId(anon.getUserId())
                .joinedAt(Instant.now())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(teamConv.getConversationId())
                .userId(sakiko.getUserId())
                .joinedAt(Instant.now())
                .build());

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello team!");
        ChatDtos.ChatMessage sent = chatService.sendMessage(teamConv.getConversationId(), tomori.getUserId(), request);

        assertThat(sent.getPeerReadStatus()).isNull();
    }

    @Test
    @DisplayName("群聊标记已读不触发peerRead通知")
    void markMessagesRead_teamChat_noPeerReadNotification() {
        User sakiko = createUser("sakiko@mygo.test", "祥子");
        Conversation teamConv = Conversation.builder()
                .conversationId(UUID.randomUUID().toString())
                .kind(ConversationKind.team)
                .title("Ave Mujica")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        conversationRepository.save(teamConv);
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(teamConv.getConversationId())
                .userId(tomori.getUserId())
                .joinedAt(Instant.now())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(teamConv.getConversationId())
                .userId(anon.getUserId())
                .joinedAt(Instant.now())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(teamConv.getConversationId())
                .userId(sakiko.getUserId())
                .joinedAt(Instant.now())
                .build());

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hi team!");
        ChatDtos.ChatMessage sent = chatService.sendMessage(teamConv.getConversationId(), tomori.getUserId(), request);

        int peerReadsBefore = capturingNotification().getPeerReads().size();
        chatService.markMessagesRead(anon.getUserId(), List.of(sent.getMessageId()));

        assertThat(capturingNotification().getPeerReads()).hasSize(peerReadsBefore);
    }

    @Test
    @DisplayName("重复标记已读幂等推送peerRead通知")
    void markMessagesRead_alreadyReadMessages_stillTriggersPeerRead() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello!");
        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        chatService.markMessagesRead(anon.getUserId(), List.of(sent.getMessageId()));
        int firstPeerReadCount = capturingNotification().getPeerReads().size();

        chatService.markMessagesRead(anon.getUserId(), List.of(sent.getMessageId()));

        assertThat(capturingNotification().getPeerReads()).hasSize(firstPeerReadCount + 1);

        var result = chatService.listMessages(conversation.getConversationId(), tomori.getUserId(), 1, 20);
        assertThat(result.getItems().getFirst().getPeerReadStatus()).isEqualTo("read");
    }

    @Test
    @DisplayName("对方发消息后查看自己的消息也有peerReadStatus=unread")
    void listMessages_peerSeesOwnPeerReadStatus() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hi from Anon!");
        ChatDtos.ChatMessage sent =
                chatService.sendMessage(conversation.getConversationId(), anon.getUserId(), request);

        var result = chatService.listMessages(conversation.getConversationId(), anon.getUserId(), 1, 20);

        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getItems().stream()
                        .anyMatch(m ->
                                m.getMessageId().equals(sent.getMessageId()) && "unread".equals(m.getPeerReadStatus())))
                .isTrue();
    }

    @Test
    @DisplayName("双方互发消息后各自看到对方的已读状态")
    void listMessages_bothSides_eachSeesOwnPeerReadStatus() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("From Tomori");
        ChatDtos.ChatMessage tomoriMsg =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        request.setText("From Anon");
        ChatDtos.ChatMessage anonMsg =
                chatService.sendMessage(conversation.getConversationId(), anon.getUserId(), request);

        var tomoriView = chatService.listMessages(conversation.getConversationId(), tomori.getUserId(), 1, 20);
        assertThat(tomoriView.getItems().stream()
                        .anyMatch(m -> m.getMessageId().equals(tomoriMsg.getMessageId())
                                && "unread".equals(m.getPeerReadStatus())))
                .isTrue();

        var anonView = chatService.listMessages(conversation.getConversationId(), anon.getUserId(), 1, 20);
        assertThat(anonView.getItems().stream()
                        .anyMatch(m -> m.getMessageId().equals(anonMsg.getMessageId())
                                && "unread".equals(m.getPeerReadStatus())))
                .isTrue();

        chatService.markMessagesRead(tomori.getUserId(), List.of(anonMsg.getMessageId()));

        anonView = chatService.listMessages(conversation.getConversationId(), anon.getUserId(), 1, 20);
        assertThat(anonView.getItems().stream()
                        .anyMatch(m -> m.getMessageId().equals(anonMsg.getMessageId())
                                && "read".equals(m.getPeerReadStatus())))
                .isTrue();
        assertThat(anonView.getItems().stream()
                        .anyMatch(m ->
                                m.getMessageId().equals(tomoriMsg.getMessageId()) && m.getPeerReadStatus() == null))
                .isTrue();
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
    @DisplayName("转发消息 - 非原会话成员转发应抛 MESSAGE_NOT_VISIBLE")
    void forwardMessage_notMemberInSourceConversation() {
        Conversation sourceConv = Conversation.builder()
                .conversationId(UUID.randomUUID().toString())
                .kind(ConversationKind.friend)
                .title("燈 & 立希")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        conversationRepository.save(sourceConv);
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(sourceConv.getConversationId())
                .userId(tomori.getUserId())
                .joinedAt(Instant.now())
                .build());

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("机密消息");
        ChatDtos.ChatMessage original =
                chatService.sendMessage(sourceConv.getConversationId(), tomori.getUserId(), request);

        assertThatThrownBy(() -> chatService.forwardMessage(
                        original.getMessageId(), anon.getUserId(), List.of(conversation.getConversationId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not visible");
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

    @Test
    @DisplayName("转发图片消息 - 为目标会话创建独立 MediaFile 副本，accessPolicy 为 conversationMember")
    void forwardMessage_image() {
        // 创建目标会话
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

        // 发送原图片消息
        UUID originalMediaId = UUID.randomUUID();
        MediaFile mediaFile = MediaFile.builder()
                .mediaId(originalMediaId)
                .fileName("forward-me.png")
                .contentType("image/png")
                .sizeBytes(512L)
                .usage(MediaUsage.chatImage)
                .storagePath("/test/forward-me.png")
                .visibility(MediaVisibility.privateVisible)
                .accessPolicy(MediaAccessPolicy.conversationMember)
                .accessScopeId(conversation.getConversationId())
                .uploadedBy(tomori.getUserId())
                .uploadedAt(Instant.now())
                .build();
        entityManager.persist(mediaFile);
        entityManager.flush();

        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.image);
        request.setImageMediaId(originalMediaId);

        ChatDtos.ChatMessage original =
                chatService.sendMessage(conversation.getConversationId(), tomori.getUserId(), request);

        // 转发到目标会话
        List<ChatDtos.ChatMessage> result = chatService.forwardMessage(
                original.getMessageId(), tomori.getUserId(), List.of(targetConv.getConversationId()));

        assertThat(result).hasSize(1);
        var forwardedDto = result.getFirst();
        assertThat(forwardedDto.getConversationId()).isEqualTo(targetConv.getConversationId());

        // 强制刷新并清除持久化上下文
        entityManager.flush();
        entityManager.clear();

        // 直接从 DB 查询转发消息以绕过懒加载代理问题
        var forwardedMessage =
                chatMessageRepository.findById(forwardedDto.getMessageId()).orElseThrow();
        assertThat(forwardedMessage.getImageMediaId()).isNotNull();
        assertThat(forwardedMessage.getImageMediaId()).isNotEqualTo(originalMediaId);

        // 验证新 MediaFile 的 accessPolicy 为 conversationMember，scope 为目标会话
        var newMediaFile =
                mediaFileRepository.findById(forwardedMessage.getImageMediaId()).orElseThrow();
        assertThat(newMediaFile.getAccessPolicy()).isEqualTo(MediaAccessPolicy.conversationMember);
        assertThat(newMediaFile.getAccessScopeId()).isEqualTo(targetConv.getConversationId());
        assertThat(newMediaFile.getStoragePath()).isEqualTo(mediaFile.getStoragePath());
        assertThat(newMediaFile.getUploadedBy()).isEqualTo(tomori.getUserId());

        // 验证原 MediaFile 不变
        var refreshedOriginal = mediaFileRepository.findById(originalMediaId).orElseThrow();
        assertThat(refreshedOriginal.getAccessPolicy()).isEqualTo(MediaAccessPolicy.conversationMember);
        assertThat(refreshedOriginal.getAccessScopeId()).isEqualTo(conversation.getConversationId());
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

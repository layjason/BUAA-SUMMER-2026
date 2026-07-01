package io.github.layjason.mayoistar.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.entity.chat.Conversation;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.chat.ConversationMember;
import io.github.layjason.mayoistar.entity.chat.MessageKind;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * ChatController 集成测试。
 *
 * <p>类职责：验证 ChatController 端点已连接到 ChatService 返回真实数据，而非占位 mock。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatControllerIntegrationTest {

    @Autowired
    private ChatController chatController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    private User tomori;
    private Conversation conversation;
    private String tomoriId;

    @BeforeEach
    void setUp() {
        tomori = createUser("tomori@mygo.test", "燈");
        tomoriId = tomori.getUserId();
        User anon = createUser("anon@mygo.test", "愛音");

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
                .userId(tomoriId)
                .joinedAt(Instant.now())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(conversation.getConversationId())
                .userId(anon.getUserId())
                .joinedAt(Instant.now())
                .build());

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        tomoriId, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_personal"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("发送消息不应返回占位数据")
    void sendMessageReturnsRealData() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("迷子でもいい、迷子でも進め！");

        var response = chatController.sendMessage(conversation.getConversationId(), request);
        ChatDtos.ChatMessage message = response.getBody().getData();

        assertThat(message.getMessageId()).as("发送消息不应返回占位 messageId").isNotEqualTo("message-placeholder");
        assertThat(message.getText()).as("消息文本应为实际内容").isEqualTo("迷子でもいい、迷子でも進め！");
        assertThat(message.getConversationId()).as("会话ID应为实际会话").isEqualTo(conversation.getConversationId());
    }

    @Test
    @DisplayName("获取消息列表不应返回占位数据")
    void listMessagesReturnsRealData() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello!");
        chatController.sendMessage(conversation.getConversationId(), request);

        var response = chatController.listMessages(conversation.getConversationId(), null, 20);
        var pageResult = response.getBody().getData();

        assertThat(pageResult.getItems()).as("消息列表不应为空").isNotEmpty();
        assertThat(pageResult.getItems().getFirst().getMessageId())
                .as("消息不应为占位数据")
                .isNotEqualTo("message-placeholder");
    }

    @Test
    @DisplayName("撤回消息不应返回占位数据")
    void recallMessageReturnsRealData() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("間違ったメッセージ");
        var sendResponse = chatController.sendMessage(conversation.getConversationId(), request);
        String messageId = sendResponse.getBody().getData().getMessageId();

        var recallResponse = chatController.recallMessage(messageId);
        ChatDtos.ChatMessage recalled = recallResponse.getBody().getData();

        assertThat(recalled.getMessageId()).as("撤回消息不应返回占位 messageId").isNotEqualTo("message-placeholder");
        assertThat(recalled.getRecalled()).as("消息应被标记为已撤回").isTrue();
    }

    @Test
    @DisplayName("标记已读不应返回占位数据")
    void markMessagesReadReturnsRealData() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("読んでください");
        var sendResponse = chatController.sendMessage(conversation.getConversationId(), request);
        String messageId = sendResponse.getBody().getData().getMessageId();

        ChatDtos.MarkMessagesReadRequest readRequest = new ChatDtos.MarkMessagesReadRequest();
        readRequest.setMessageIds(List.of(messageId));
        var response = chatController.markMessagesRead(readRequest);

        assertThat(response.getBody().getData()).as("已读标记响应不应为空").isNotEmpty();
    }

    @Test
    @DisplayName("转发消息不应返回占位数据")
    void forwardMessageReturnsRealData() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("転送してください");
        var sendResponse = chatController.sendMessage(conversation.getConversationId(), request);
        String messageId = sendResponse.getBody().getData().getMessageId();

        ChatDtos.ForwardMessageRequest forwardRequest = new ChatDtos.ForwardMessageRequest();
        forwardRequest.setTargetConversationIds(List.of(conversation.getConversationId()));
        var response = chatController.forwardMessage(messageId, forwardRequest);

        assertThat(response.getBody().getData()).as("转发消息响应不应为空").isNotEmpty();
        assertThat(response.getBody().getData().getFirst().getMessageId())
                .as("转发消息不应为占位数据")
                .isNotEqualTo("message-placeholder");
    }

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

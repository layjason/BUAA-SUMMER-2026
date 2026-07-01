package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.api.chat.ChatDtos;
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
class ConversationListIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

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

    @Test
    @DisplayName("会话列表应返回用户所属会话及未读计数")
    void listConversationsWithUnreadCount() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hello 燈!");
        chatService.sendMessage(conversation.getConversationId(), anon.getUserId(), request);

        var result = chatService.listConversations(tomori.getUserId(), 1, 20);

        assertThat(result.getItems()).as("会话列表不应为空").isNotEmpty();

        var summary = result.getItems().getFirst();
        assertThat(summary.getConversationId()).as("会话ID应为实际会话").isEqualTo(conversation.getConversationId());
        assertThat(summary.getKind()).as("会话类型应为 friend").isEqualTo(ConversationKind.friend);
        assertThat(summary.getUnreadCount()).as("燈收到愛音的消息，未读计数应为1").isEqualTo(1);
        assertThat(summary.getLastMessagePreview()).as("最后一条消息预览应为实际内容").isEqualTo("Hello 燈!");
    }

    @Test
    @DisplayName("已读消息后未读计数应为0")
    void conversationWithReadMessagesHasZeroUnread() {
        ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest();
        request.setKind(MessageKind.text);
        request.setText("Hi!");
        var sent = chatService.sendMessage(conversation.getConversationId(), anon.getUserId(), request);

        chatService.markMessagesRead(tomori.getUserId(), java.util.List.of(sent.getMessageId()));

        var result = chatService.listConversations(tomori.getUserId(), 1, 20);
        assertThat(result.getItems().getFirst().getUnreadCount())
                .as("标记已读后未读计数应为0")
                .isEqualTo(0);
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

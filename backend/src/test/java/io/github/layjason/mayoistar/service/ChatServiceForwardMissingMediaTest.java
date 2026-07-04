package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.entity.chat.Conversation;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.chat.ConversationMember;
import io.github.layjason.mayoistar.entity.chat.MessageKind;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.ChatMessageRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单独测试 forwardMessage 在原始媒体文件不存在时的降级行为。
 *
 * <p>需关闭 H2 引用完整性检查插入含失效 FK 的测试数据。
 * @DirtiesContext 确保此测试的脏数据不影响其他测试类。
 */
@Transactional
@DirtiesContext
class ChatServiceForwardMissingMediaTest extends AbstractIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User tomori;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        tomori = createUser("tomori@mygo.test", "燈");
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
    }

    @Test
    @DisplayName("原始媒体文件已删除时，转发图片消息降级为无图消息，不复用失效 mediaId")
    void forwardMessageMissingMediaFallback() {
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

        String messageId = UUID.randomUUID().toString();
        UUID fakeMediaId = UUID.randomUUID();
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        entityManager
                .createNativeQuery(
                        "INSERT INTO chat_messages (message_id, conversation_id, sender_id, kind, image_media_id, recalled, sent_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")
                .setParameter(1, messageId)
                .setParameter(2, conversation.getConversationId())
                .setParameter(3, tomori.getUserId())
                .setParameter(4, MessageKind.image.name())
                .setParameter(5, fakeMediaId.toString())
                .setParameter(6, false)
                .setParameter(7, Instant.now())
                .executeUpdate();
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
        entityManager.flush();

        List<ChatDtos.ChatMessage> result =
                chatService.forwardMessage(messageId, tomori.getUserId(), List.of(targetConv.getConversationId()));

        assertThat(result).hasSize(1);
        var forwardedDto = result.getFirst();
        assertThat(forwardedDto.getConversationId()).isEqualTo(targetConv.getConversationId());

        entityManager.flush();
        entityManager.clear();

        var forwardedMessage =
                chatMessageRepository.findById(forwardedDto.getMessageId()).orElseThrow();
        assertThat(forwardedMessage.getImageMediaId()).isNull();
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

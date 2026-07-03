package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.FriendRequestRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * FriendRequestService 集成测试。
 *
 * <p>类职责：验证好友申请通过后自动创建会话的级联行为。
 */
@Transactional
class FriendRequestServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FriendRequestService friendRequestService;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    private User tomori;
    private User anon;
    private String requestId;

    @BeforeEach
    void setUp() {
        tomori = createUser("tomori@mygo.test", "燈");
        anon = createUser("anon@mygo.test", "愛音");

        var result = friendRequestService.createFriendRequest(
                tomori.getUserId(), anon.getUserId(), FriendRequestSource.profile, "我想和你一辈子组乐队");
        requestId = result.getRequestId();
    }

    @Test
    @DisplayName("接受好友申请后自动创建好友会话")
    void acceptFriendRequestCreatesConversation() {
        friendRequestService.decideFriendRequest(anon.getUserId(), requestId, true);

        var conversations = conversationRepository.findAll().stream()
                .filter(conversation -> conversation.getKind() == ConversationKind.friend)
                .toList();
        assertThat(conversations).as("接受好友申请后应创建会话").hasSize(1);

        var conversation = conversations.getFirst();
        assertThat(conversation.getKind()).as("好友会话类型应为 friend").isEqualTo(ConversationKind.friend);

        var members = conversationMemberRepository.findByConversationId(conversation.getConversationId());
        assertThat(members).as("会话应包含两位成员").hasSize(2);

        assertThat(members)
                .extracting(m -> m.getUserId())
                .as("会话成员应包含双方用户")
                .containsExactlyInAnyOrder(tomori.getUserId(), anon.getUserId());
    }

    @Test
    @DisplayName("拒绝好友申请不应创建会话")
    void rejectFriendRequestDoesNotCreateConversation() {
        friendRequestService.decideFriendRequest(anon.getUserId(), requestId, false);

        var conversations = conversationRepository.findAll().stream()
                .filter(conversation -> conversation.getKind() == ConversationKind.friend)
                .toList();
        assertThat(conversations).as("拒绝好友申请不应创建会话").isEmpty();
    }

    @Test
    @DisplayName("好友申请通过后 pending 状态应变为 accepted")
    void acceptFriendRequestChangesStatus() {
        var result = friendRequestService.decideFriendRequest(anon.getUserId(), requestId, true);
        assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.accepted);
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

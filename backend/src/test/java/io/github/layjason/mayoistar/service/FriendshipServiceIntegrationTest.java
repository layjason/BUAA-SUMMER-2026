package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * FriendshipService 集成测试。
 *
 * <p>类职责：验证删除好友时的级联清理行为。
 */
@Transactional
class FriendshipServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private FriendRequestService friendRequestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    private User tomori;
    private User anon;
    private String conversationId;

    @BeforeEach
    void setUp() {
        tomori = createUser("tomori@mygo.test", "燈");
        anon = createUser("anon@mygo.test", "愛音");

        var result = friendRequestService.createFriendRequest(
                tomori.getUserId(), anon.getUserId(), FriendRequestSource.profile, "我想和你一辈子组乐队");
        friendRequestService.decideFriendRequest(anon.getUserId(), result.getRequestId(), true);

        conversationId = conversationRepository.findAll().stream()
                .filter(c -> c.getKind() == ConversationKind.friend)
                .findFirst()
                .orElseThrow()
                .getConversationId();
    }

    @Test
    @DisplayName("删除好友后应清理好友会话")
    void deleteFriendCleansUpConversation() {
        friendshipService.deleteFriend(tomori.getUserId(), anon.getUserId());

        assertThat(conversationRepository.findById(conversationId))
                .as("删除好友后会话应被删除")
                .isEmpty();

        assertThat(conversationMemberRepository.findByConversationId(conversationId))
                .as("删除好友后会话成员应被清理")
                .isEmpty();

        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .as("删除好友后好友关系应不存在")
                .isFalse();
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

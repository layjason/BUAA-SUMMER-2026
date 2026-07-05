package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.FollowRepository;
import io.github.layjason.mayoistar.repository.FriendRequestRepository;
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
 * BlacklistService 集成测试。
 *
 * <p>类职责：验证拉黑时级联删除好友关系、关注关系、pending 好友申请的清理行为。
 */
@Transactional
class BlacklistServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BlacklistService blacklistService;

    @Autowired
    private FriendRequestService friendRequestService;

    @Autowired
    private FollowService followService;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private BlacklistRepository blacklistRepository;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    private User tomori;
    private User anon;

    @BeforeEach
    void setUp() {
        tomori = createUser("tomori@mygo.test", "燈");
        anon = createUser("anon@mygo.test", "愛音");
    }

    @Test
    @DisplayName("拉黑用户后应删除双向好友关系")
    void blockUserDeletesFriendship() {
        var result = friendRequestService.createFriendRequest(
                tomori.getUserId(), anon.getUserId(), FriendRequestSource.profile, "我想和你一辈子组乐队");
        friendRequestService.decideFriendRequest(anon.getUserId(), result.getRequestId(), true);

        blacklistService.blockUser(tomori.getUserId(), anon.getUserId());

        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .as("拉黑后用户A→B好友关系应被删除")
                .isFalse();
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(anon.getUserId(), tomori.getUserId()))
                .as("拉黑后用户B→A好友关系应被删除")
                .isFalse();
    }

    @Test
    @DisplayName("拉黑用户后应删除pending好友申请")
    void blockUserDeletesPendingFriendRequests() {
        friendRequestService.createFriendRequest(
                tomori.getUserId(), anon.getUserId(), FriendRequestSource.profile, "交个朋友？");

        blacklistService.blockUser(anon.getUserId(), tomori.getUserId());

        assertThat(friendRequestRepository.existsByRequesterIdAndTargetUserIdAndStatus(
                        tomori.getUserId(), anon.getUserId(), FriendRequestStatus.pending))
                .as("拉黑后pending好友申请应被删除")
                .isFalse();
    }

    @Test
    @DisplayName("拉黑用户后应删除关注关系")
    void blockUserDeletesFollowRelations() {
        followService.followUser(tomori.getUserId(), anon.getUserId());

        blacklistService.blockUser(tomori.getUserId(), anon.getUserId());

        assertThat(followRepository.existsByFollowerIdAndFollowedId(tomori.getUserId(), anon.getUserId()))
                .as("拉黑后关注关系应被删除")
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

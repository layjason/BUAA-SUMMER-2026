package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Blacklist;
import io.github.layjason.mayoistar.entity.social.Follow;
import io.github.layjason.mayoistar.entity.social.FriendRequest;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.entity.social.FriendshipSource;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.FollowRepository;
import io.github.layjason.mayoistar.repository.FriendRequestRepository;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
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
class SocialServiceTest {

    @Autowired
    private SocialService socialService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PersonalProfileRepository personalProfileRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private BlacklistRepository blacklistRepository;

    private User tomori;
    private User anon;
    private User raana;

    @BeforeEach
    void setUp() {
        tomori = createUser("tomori@mygo.test", "燈");
        anon = createUser("anon@mygo.test", "愛音");
        raana = createUser("raana@mygo.test", "楽奈");

        createProfile(tomori.getUserId());
        createProfile(anon.getUserId());
        createProfile(raana.getUserId());
    }

    // ========================================
    // getUserProfile Tests
    // ========================================

    @Test
    @DisplayName("获取个人主页 - 正常返回公开资料")
    void getUserProfile_success() {
        IdentityDtos.PublicUserProfile profile = socialService.getUserProfile(tomori.getUserId(), anon.getUserId());

        assertThat(profile.getUserId()).isEqualTo(anon.getUserId());
        assertThat(profile.getNickname()).isEqualTo("愛音");
        assertThat(profile.getKind()).isEqualTo(UserKind.personal);
    }

    @Test
    @DisplayName("获取个人主页 - 用户不存在抛 USER_NOT_VISIBLE")
    void getUserProfile_userNotFound() {
        assertThatThrownBy(() -> socialService.getUserProfile(tomori.getUserId(), "nonexistent"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not visible");
    }

    @Test
    @DisplayName("获取个人主页 - 黑名单关系阻止查看")
    void getUserProfile_blacklisted() {
        blacklistRepository.save(Blacklist.builder()
                .blacklistId(UUID.randomUUID().toString())
                .blockerId(anon.getUserId())
                .blockedUserId(tomori.getUserId())
                .createdAt(Instant.now())
                .build());

        assertThatThrownBy(() -> socialService.getUserProfile(tomori.getUserId(), anon.getUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Blacklist relation");
    }

    // ========================================
    // followUser Tests
    // ========================================

    @Test
    @DisplayName("关注用户 - 成功创建关注关系")
    void followUser_success() {
        SocialDtos.FollowRelation result = socialService.followUser(tomori.getUserId(), anon.getUserId());

        assertThat(result.getFollowing()).isTrue();
        assertThat(result.getMutual()).isFalse();
        assertThat(result.getFriendshipCreated()).isFalse();
        assertThat(followRepository.existsByFollowerIdAndFollowedId(tomori.getUserId(), anon.getUserId()))
                .isTrue();
    }

    @Test
    @DisplayName("关注用户 - 互相关注自动升级为好友")
    void followUser_mutualFollowUpgradeToFriend() {
        followRepository.save(Follow.builder()
                .followId(UUID.randomUUID().toString())
                .followerId(anon.getUserId())
                .followedId(tomori.getUserId())
                .createdAt(Instant.now())
                .build());

        SocialDtos.FollowRelation result = socialService.followUser(tomori.getUserId(), anon.getUserId());

        assertThat(result.getFollowing()).isTrue();
        assertThat(result.getMutual()).isTrue();
        assertThat(result.getFriendshipCreated()).isTrue();
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .isTrue();
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(anon.getUserId(), tomori.getUserId()))
                .isTrue();
    }

    @Test
    @DisplayName("关注用户 - 互关时双方已是手动好友不重复创建")
    void followUser_mutualFollowWhenAlreadyFriends() {
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(tomori.getUserId())
                .friendUserId(anon.getUserId())
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build());
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(anon.getUserId())
                .friendUserId(tomori.getUserId())
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build());
        followRepository.save(Follow.builder()
                .followId(UUID.randomUUID().toString())
                .followerId(anon.getUserId())
                .followedId(tomori.getUserId())
                .createdAt(Instant.now())
                .build());

        SocialDtos.FollowRelation result = socialService.followUser(tomori.getUserId(), anon.getUserId());

        assertThat(result.getFollowing()).isTrue();
        assertThat(result.getMutual()).isTrue();
        assertThat(result.getFriendshipCreated()).isFalse();
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .isTrue();
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(anon.getUserId(), tomori.getUserId()))
                .isTrue();
    }

    @Test
    @DisplayName("关注用户 - 重复关注抛 FOLLOW_ALREADY_EXISTS")
    void followUser_alreadyFollowing() {
        socialService.followUser(tomori.getUserId(), anon.getUserId());

        assertThatThrownBy(() -> socialService.followUser(tomori.getUserId(), anon.getUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    // ========================================
    // unfollowUser Tests
    // ========================================

    @Test
    @DisplayName("取消关注 - 互关好友关系同步解除")
    void unfollowUser_mutualFollowDeletesFriendship() {
        followRepository.save(Follow.builder()
                .followId(UUID.randomUUID().toString())
                .followerId(anon.getUserId())
                .followedId(tomori.getUserId())
                .createdAt(Instant.now())
                .build());

        socialService.followUser(tomori.getUserId(), anon.getUserId());
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .isTrue();

        socialService.unfollowUser(tomori.getUserId(), anon.getUserId());
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .isFalse();
        assertThat(followRepository.existsByFollowerIdAndFollowedId(tomori.getUserId(), anon.getUserId()))
                .isFalse();
    }

    @Test
    @DisplayName("取消关注 - 手动好友关系不因取消关注而删除")
    void unfollowUser_manualFriendshipPreserved() {
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(tomori.getUserId())
                .friendUserId(anon.getUserId())
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build());
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(anon.getUserId())
                .friendUserId(tomori.getUserId())
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build());

        socialService.followUser(tomori.getUserId(), anon.getUserId());
        socialService.unfollowUser(tomori.getUserId(), anon.getUserId());

        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .isTrue();
    }

    @Test
    @DisplayName("取消关注 - 未关注抛 FOLLOW_NOT_FOUND")
    void unfollowUser_notFollowing() {
        assertThatThrownBy(() -> socialService.unfollowUser(tomori.getUserId(), anon.getUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not exist");
    }

    // ========================================
    // Friend Request Tests
    // ========================================

    @Test
    @DisplayName("发送好友申请 - 成功创建")
    void createFriendRequest_success() {
        SocialDtos.FriendRequest result =
                socialService.createFriendRequest(tomori.getUserId(), anon.getUserId(), "profile", "Hi");

        assertThat(result.getRequesterId()).isEqualTo(tomori.getUserId());
        assertThat(result.getTargetUserId()).isEqualTo(anon.getUserId());
        assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.pending);
    }

    @Test
    @DisplayName("发送好友申请 - 已是好友抛 FRIENDSHIP_STATE_INVALID")
    void createFriendRequest_alreadyFriends() {
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(tomori.getUserId())
                .friendUserId(anon.getUserId())
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build());

        assertThatThrownBy(
                        () -> socialService.createFriendRequest(tomori.getUserId(), anon.getUserId(), "profile", "Hi"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Friendship state");
    }

    @Test
    @DisplayName("处理好友申请 - 同意建立好友关系")
    void decideFriendRequest_accept() {
        FriendRequest request = saveFriendRequest(tomori.getUserId(), anon.getUserId());

        SocialDtos.FriendRequest result =
                socialService.decideFriendRequest(request.getRequestId(), anon.getUserId(), true);

        assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.accepted);
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .isTrue();
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(anon.getUserId(), tomori.getUserId()))
                .isTrue();
    }

    @Test
    @DisplayName("处理好友申请 - 拒绝不建立好友关系")
    void decideFriendRequest_reject() {
        FriendRequest request = saveFriendRequest(tomori.getUserId(), anon.getUserId());

        SocialDtos.FriendRequest result =
                socialService.decideFriendRequest(request.getRequestId(), anon.getUserId(), false);

        assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.rejected);
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .isFalse();
    }

    // ========================================
    // Friends Tests
    // ========================================

    @Test
    @DisplayName("获取好友列表 - 返回好友")
    void listFriends_returnsFriends() {
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(tomori.getUserId())
                .friendUserId(anon.getUserId())
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build());

        var result = socialService.listFriends(tomori.getUserId(), 1, 20);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getUserId()).isEqualTo(anon.getUserId());
        assertThat(result.getItems().getFirst().getNickname()).isEqualTo("愛音");
    }

    @Test
    @DisplayName("更新好友备注 - 成功更新")
    void updateFriendRemark_success() {
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(tomori.getUserId())
                .friendUserId(anon.getUserId())
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build());

        SocialDtos.FriendItem result =
                socialService.updateFriendRemark(tomori.getUserId(), anon.getUserId(), "Bobby", List.of("college"));

        assertThat(result.getRemark()).isEqualTo("Bobby");
        assertThat(result.getGroupTags()).contains("college");
    }

    @Test
    @DisplayName("删除好友 - 双向解除")
    void deleteFriend_bilateral() {
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(tomori.getUserId())
                .friendUserId(anon.getUserId())
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build());
        friendshipRepository.save(Friendship.builder()
                .friendshipId(UUID.randomUUID().toString())
                .userId(anon.getUserId())
                .friendUserId(tomori.getUserId())
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build());

        socialService.deleteFriend(tomori.getUserId(), anon.getUserId());

        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(tomori.getUserId(), anon.getUserId()))
                .isFalse();
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId(anon.getUserId(), tomori.getUserId()))
                .isFalse();
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

    private void createProfile(String userId) {
        User userRef = userRepository.findById(userId).orElseThrow();
        PersonalProfile profile = new PersonalProfile();
        profile.setUser(userRef);
        profile.setReputationScore(100);
        profile.setUpdatedAt(Instant.now());
        personalProfileRepository.save(profile);
    }

    private FriendRequest saveFriendRequest(String requesterId, String targetUserId) {
        FriendRequest request = FriendRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .requesterId(requesterId)
                .targetUserId(targetUserId)
                .source(FriendRequestSource.profile)
                .status(FriendRequestStatus.pending)
                .createdAt(Instant.now())
                .build();
        return friendRequestRepository.save(request);
    }
}

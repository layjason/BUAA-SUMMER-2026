package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.social.Follow;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.entity.social.FriendshipSource;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.FollowRepository;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FollowService 单元测试。
 *
 * <p>类职责：验证关注、取消关注和互关好友升级规则。
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private BlacklistRepository blacklistRepository;

    @Mock
    private UserRepository userRepository;

    private FollowService service;

    @BeforeEach
    void setUp() {
        service = new FollowServiceImpl(followRepository, friendshipRepository, blacklistRepository, userRepository);
    }

    @Test
    @DisplayName("关注用户 - 成功创建关注关系")
    void followUserSuccess() {
        when(userRepository.existsById("user-b")).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowedId("user-a", "user-b"))
                .thenReturn(false);
        when(followRepository.existsByFollowerIdAndFollowedId("user-b", "user-a"))
                .thenReturn(false);

        var result = service.followUser("user-a", "user-b");

        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(captor.capture());
        assertThat(captor.getValue().getFollowerId()).isEqualTo("user-a");
        assertThat(result.getFollowing()).isTrue();
        assertThat(result.getMutual()).isFalse();
        assertThat(result.getFriendshipCreated()).isFalse();
    }

    @Test
    @DisplayName("关注用户 - 互相关注自动升级好友")
    void followUserMutualCreatesFriendship() {
        when(userRepository.existsById("user-b")).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowedId("user-a", "user-b"))
                .thenReturn(false);
        when(followRepository.existsByFollowerIdAndFollowedId("user-b", "user-a"))
                .thenReturn(true);
        when(friendshipRepository.existsByUserIdAndFriendUserId("user-a", "user-b"))
                .thenReturn(false);

        var result = service.followUser("user-a", "user-b");

        verify(friendshipRepository, org.mockito.Mockito.times(2)).save(any(Friendship.class));
        assertThat(result.getMutual()).isTrue();
        assertThat(result.getFriendshipCreated()).isTrue();
    }

    @Test
    @DisplayName("关注用户 - 已是手动好友时互关不重复建好友")
    void followUserMutualWhenAlreadyFriendsDoesNotDuplicateFriendship() {
        when(userRepository.existsById("user-b")).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowedId("user-a", "user-b"))
                .thenReturn(false);
        when(followRepository.existsByFollowerIdAndFollowedId("user-b", "user-a"))
                .thenReturn(true);
        when(friendshipRepository.existsByUserIdAndFriendUserId("user-a", "user-b"))
                .thenReturn(true);

        var result = service.followUser("user-a", "user-b");

        verify(friendshipRepository, never()).save(any(Friendship.class));
        assertThat(result.getFriendshipCreated()).isFalse();
    }

    @Test
    @DisplayName("关注用户 - 重复关注抛异常")
    void followUserDuplicateThrowsException() {
        when(userRepository.existsById("user-b")).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowedId("user-a", "user-b"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.followUser("user-a", "user-b"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40002);
    }

    @Test
    @DisplayName("取消关注 - 互关好友关系同步解除")
    void unfollowUserClearsMutualFriendship() {
        Friendship friendship = Friendship.builder()
                .userId("user-a")
                .friendUserId("user-b")
                .source(FriendshipSource.mutualFollow)
                .createdAt(Instant.now())
                .build();
        when(userRepository.existsById("user-b")).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowedId("user-a", "user-b"))
                .thenReturn(true);
        when(friendshipRepository.findByUserIdAndFriendUserId("user-a", "user-b"))
                .thenReturn(Optional.of(friendship));

        var result = service.unfollowUser("user-a", "user-b");

        verify(followRepository).deleteByFollowerIdAndFollowedId("user-a", "user-b");
        verify(friendshipRepository).deleteBilateral("user-a", "user-b");
        assertThat(result.getFollowing()).isFalse();
    }

    @Test
    @DisplayName("取消关注 - 手动好友不随取消关注删除")
    void unfollowUserPreservesManualFriendship() {
        Friendship friendship = Friendship.builder()
                .userId("user-a")
                .friendUserId("user-b")
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build();
        when(userRepository.existsById("user-b")).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowedId("user-a", "user-b"))
                .thenReturn(true);
        when(friendshipRepository.findByUserIdAndFriendUserId("user-a", "user-b"))
                .thenReturn(Optional.of(friendship));

        service.unfollowUser("user-a", "user-b");

        verify(friendshipRepository, never()).deleteBilateral("user-a", "user-b");
    }

    @Test
    @DisplayName("关注用户 - 黑名单关系阻止关注")
    void followUserBlacklistThrowsException() {
        when(userRepository.existsById("user-b")).thenReturn(true);
        when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                .thenReturn(false);
        when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-b", "user-a"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.followUser("user-a", "user-b"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40001);
    }
}

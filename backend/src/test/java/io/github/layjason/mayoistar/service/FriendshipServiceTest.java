package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.entity.social.FriendshipSource;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ChatMessageRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.MessageReadRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * FriendshipService 单元测试。
 *
 * <p>类职责：验证好友列表查询、备注更新、删除好友的业务逻辑和异常路径。
 */
@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMemberRepository conversationMemberRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MessageReadRepository messageReadRepository;

    @Mock
    private PersonalProfileRepository personalProfileRepository;

    @Mock
    private MediaAccessService mediaAccessService;

    private FriendshipService service;

    @BeforeEach
    void setUp() {
        service = new FriendshipServiceImpl(
                friendshipRepository,
                userRepository,
                personalProfileRepository,
                conversationRepository,
                conversationMemberRepository,
                chatMessageRepository,
                messageReadRepository,
                mediaAccessService);
    }

    private User buildUser(String userId, String nickname) {
        return User.builder()
                .userId(userId)
                .email(userId + "@test.com")
                .nickname(nickname)
                .passwordHash("hash")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("查询好友列表")
    class ListFriendsTests {

        @Test
        @DisplayName("分页查询好友列表并填充昵称")
        void listFriendsWithNickname() {
            Friendship fs1 = Friendship.builder()
                    .friendshipId("fs-1")
                    .userId("user-a")
                    .friendUserId("user-b")
                    .source(FriendshipSource.manualRequest)
                    .remark("好友B")
                    .groupTags(List.of("同事"))
                    .createdAt(Instant.now())
                    .build();
            Page<Friendship> page = new PageImpl<>(List.of(fs1), PageRequest.of(0, 20), 1);
            when(friendshipRepository.findByUserIdOrderByCreatedAtDesc("user-a", PageRequest.of(0, 20)))
                    .thenReturn(page);
            when(userRepository.findById("user-b")).thenReturn(Optional.of(buildUser("user-b", "userB")));

            var result = service.listFriends("user-a", 1, 20, null);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getUserId()).isEqualTo("user-b");
            assertThat(result.getItems().get(0).getNickname()).isEqualTo("userB");
            assertThat(result.getItems().get(0).getRemark()).isEqualTo("好友B");
            assertThat(result.getItems().get(0).getGroupTags()).containsExactly("同事");
        }
    }

    @Nested
    @DisplayName("更新好友备注")
    class UpdateFriendRemarkTests {

        @Test
        @DisplayName("成功更新好友备注")
        void updateRemarkSuccess() {
            Friendship friendship = Friendship.builder()
                    .friendshipId("fs-1")
                    .userId("user-a")
                    .friendUserId("user-b")
                    .source(FriendshipSource.manualRequest)
                    .createdAt(Instant.now())
                    .build();
            when(friendshipRepository.findByUserIdAndFriendUserId("user-a", "user-b"))
                    .thenReturn(Optional.of(friendship));
            when(userRepository.findById("user-b")).thenReturn(Optional.of(buildUser("user-b", "userB")));

            var result = service.updateFriendRemark("user-a", "user-b", "新备注", List.of("家人"));

            assertThat(result.getRemark()).isEqualTo("新备注");
            assertThat(result.getGroupTags()).containsExactly("家人");
        }

        @Test
        @DisplayName("关系不存在时更新备注抛出异常")
        void updateNonExistentThrowsException() {
            when(friendshipRepository.findByUserIdAndFriendUserId("user-a", "user-x"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateFriendRemark("user-a", "user-x", "备注", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40004);
        }
    }

    @Nested
    @DisplayName("删除好友")
    class DeleteFriendTests {

        @Test
        @DisplayName("成功删除好友，双向关系同时删除")
        void deleteFriendBidirectional() {
            when(friendshipRepository.existsByUserIdAndFriendUserId("user-a", "user-b"))
                    .thenReturn(true);
            when(conversationMemberRepository.findCommonConversationIds("user-a", "user-b"))
                    .thenReturn(List.of());

            service.deleteFriend("user-a", "user-b");

            verify(friendshipRepository).deleteByUserIdAndFriendUserId("user-a", "user-b");
            verify(friendshipRepository).deleteByUserIdAndFriendUserId("user-b", "user-a");
        }

        @Test
        @DisplayName("关系不存在时删除抛出异常")
        void deleteNonExistentThrowsException() {
            when(friendshipRepository.existsByUserIdAndFriendUserId("user-a", "user-b"))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.deleteFriend("user-a", "user-b"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40004);
        }
    }
}

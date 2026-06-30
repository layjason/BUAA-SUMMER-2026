package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.social.FriendRequest;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.FriendRequestRepository;
import io.github.layjason.mayoistar.repository.FriendshipRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * FriendRequestService 单元测试。
 *
 * <p>类职责：验证好友申请发送、处理、查询的业务逻辑和异常路径。
 */
@ExtendWith(MockitoExtension.class)
class FriendRequestServiceTest {

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private BlacklistRepository blacklistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private FriendRequestService service;

    @BeforeEach
    void setUp() {
        service = new FriendRequestServiceImpl(
                friendRequestRepository,
                friendshipRepository,
                blacklistRepository,
                userRepository,
                notificationService);
    }

    @Nested
    @DisplayName("发送好友申请")
    class CreateFriendRequestTests {

        @Test
        @DisplayName("成功发送好友申请")
        void success() {
            when(userRepository.existsById("user-b")).thenReturn(true);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                    .thenReturn(false);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-b", "user-a"))
                    .thenReturn(false);
            when(friendshipRepository.existsByUserIdAndFriendUserId("user-a", "user-b"))
                    .thenReturn(false);
            when(friendRequestRepository.existsByRequesterIdAndTargetUserIdAndStatus(
                            "user-a", "user-b", FriendRequestStatus.pending))
                    .thenReturn(false);
            when(friendRequestRepository.existsByRequesterIdAndTargetUserIdAndStatus(
                            "user-b", "user-a", FriendRequestStatus.pending))
                    .thenReturn(false);

            var result = service.createFriendRequest("user-a", "user-b", FriendRequestSource.profile, "Hello");

            ArgumentCaptor<FriendRequest> captor = ArgumentCaptor.forClass(FriendRequest.class);
            verify(friendRequestRepository).save(captor.capture());
            verify(notificationService).notifyFriendRequestCreated(any());
            assertThat(captor.getValue().getRequesterId()).isEqualTo("user-a");
            assertThat(captor.getValue().getTargetUserId()).isEqualTo("user-b");
            assertThat(captor.getValue().getStatus()).isEqualTo(FriendRequestStatus.pending);
            assertThat(captor.getValue().getMessage()).isEqualTo("Hello");
            assertThat(result.getRequesterId()).isEqualTo("user-a");
        }

        @Test
        @DisplayName("不能给自己发好友申请")
        void selfRequestThrowsException() {
            assertThatThrownBy(() -> service.createFriendRequest("user-a", "user-a", FriendRequestSource.profile, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40000);
        }

        @Test
        @DisplayName("目标用户不存在时抛出异常")
        void nonExistentTargetThrowsException() {
            when(userRepository.existsById("user-x")).thenReturn(false);

            assertThatThrownBy(() -> service.createFriendRequest("user-a", "user-x", FriendRequestSource.profile, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40000);
        }

        @Test
        @DisplayName("已拉黑对方时抛出异常")
        void blockedByRequesterThrowsException() {
            when(userRepository.existsById("user-b")).thenReturn(true);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createFriendRequest("user-a", "user-b", FriendRequestSource.profile, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40001);
        }

        @Test
        @DisplayName("被对方拉黑时抛出异常")
        void blockedByTargetThrowsException() {
            when(userRepository.existsById("user-b")).thenReturn(true);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                    .thenReturn(false);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-b", "user-a"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createFriendRequest("user-a", "user-b", FriendRequestSource.profile, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40001);
        }

        @Test
        @DisplayName("已经是好友时抛出异常")
        void alreadyFriendsThrowsException() {
            when(userRepository.existsById("user-b")).thenReturn(true);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                    .thenReturn(false);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-b", "user-a"))
                    .thenReturn(false);
            when(friendshipRepository.existsByUserIdAndFriendUserId("user-a", "user-b"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createFriendRequest("user-a", "user-b", FriendRequestSource.profile, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40004);
        }

        @Test
        @DisplayName("已存在待处理申请时抛出异常")
        void duplicatePendingRequestThrowsException() {
            when(userRepository.existsById("user-b")).thenReturn(true);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                    .thenReturn(false);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-b", "user-a"))
                    .thenReturn(false);
            when(friendshipRepository.existsByUserIdAndFriendUserId("user-a", "user-b"))
                    .thenReturn(false);
            when(friendRequestRepository.existsByRequesterIdAndTargetUserIdAndStatus(
                            "user-a", "user-b", FriendRequestStatus.pending))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createFriendRequest("user-a", "user-b", FriendRequestSource.profile, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40006);
        }
    }

    @Nested
    @DisplayName("处理好友申请")
    class DecideFriendRequestTests {

        /**
         * 验证接受好友申请，创建双向好友关系。
         */
        @Test
        @DisplayName("接受好友申请，创建双向好友关系")
        void acceptRequestCreatesBidirectionalFriendship() {
            FriendRequest request = FriendRequest.builder()
                    .requestId("fr-1")
                    .requesterId("user-a")
                    .targetUserId("user-b")
                    .source(FriendRequestSource.profile)
                    .status(FriendRequestStatus.pending)
                    .createdAt(Instant.now())
                    .build();
            when(friendRequestRepository.findById("fr-1")).thenReturn(Optional.of(request));

            var result = service.decideFriendRequest("user-b", "fr-1", true);

            assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.accepted);

            ArgumentCaptor<Friendship> captor = ArgumentCaptor.forClass(Friendship.class);
            verify(friendshipRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            List<Friendship> saved = captor.getAllValues();
            assertThat(saved).hasSize(2);
        }

        /**
         * 验证拒绝好友申请，不创建好友关系。
         */
        @Test
        @DisplayName("拒绝好友申请，不创建好友关系")
        void rejectRequestDoesNotCreateFriendship() {
            FriendRequest request = FriendRequest.builder()
                    .requestId("fr-1")
                    .requesterId("user-a")
                    .targetUserId("user-b")
                    .source(FriendRequestSource.profile)
                    .status(FriendRequestStatus.pending)
                    .createdAt(Instant.now())
                    .build();
            when(friendRequestRepository.findById("fr-1")).thenReturn(Optional.of(request));

            var result = service.decideFriendRequest("user-b", "fr-1", false);

            assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.rejected);
            verify(friendshipRepository, never()).save(any());
        }

        /**
         * 验证非目标用户处理申请时抛出异常。
         */
        @Test
        @DisplayName("非目标用户不能处理申请")
        void nonTargetUserCannotDecide() {
            FriendRequest request = FriendRequest.builder()
                    .requestId("fr-1")
                    .requesterId("user-a")
                    .targetUserId("user-b")
                    .source(FriendRequestSource.profile)
                    .status(FriendRequestStatus.pending)
                    .createdAt(Instant.now())
                    .build();
            when(friendRequestRepository.findById("fr-1")).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.decideFriendRequest("user-c", "fr-1", true))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40005);
        }

        /**
         * 验证已处理的申请不能再次处理。
         */
        @Test
        @DisplayName("已处理的申请不能再次处理")
        void nonPendingRequestCannotBeDecided() {
            FriendRequest request = FriendRequest.builder()
                    .requestId("fr-1")
                    .requesterId("user-a")
                    .targetUserId("user-b")
                    .source(FriendRequestSource.profile)
                    .status(FriendRequestStatus.accepted)
                    .createdAt(Instant.now())
                    .build();
            when(friendRequestRepository.findById("fr-1")).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.decideFriendRequest("user-b", "fr-1", true))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40005);
        }
    }

    @Nested
    @DisplayName("查询好友申请")
    class ListRequestsTests {

        /**
         * 验证查询收到的申请。
         */
        @Test
        @DisplayName("查询收到的申请")
        void listReceivedRequests() {
            FriendRequest fr1 = FriendRequest.builder()
                    .requestId("fr-1")
                    .requesterId("user-b")
                    .targetUserId("user-a")
                    .source(FriendRequestSource.profile)
                    .status(FriendRequestStatus.pending)
                    .createdAt(Instant.now())
                    .build();
            Page<FriendRequest> page = new PageImpl<>(List.of(fr1), PageRequest.of(0, 20), 1);
            when(friendRequestRepository.findByTargetUserIdOrderByCreatedAtDesc("user-a", PageRequest.of(0, 20)))
                    .thenReturn(page);

            var result = service.listReceivedRequests("user-a", null, 1, 20);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getRequesterId()).isEqualTo("user-b");
        }

        /**
         * 验证查询已发送的申请。
         */
        @Test
        @DisplayName("查询已发送的申请")
        void listSentRequests() {
            FriendRequest fr1 = FriendRequest.builder()
                    .requestId("fr-1")
                    .requesterId("user-a")
                    .targetUserId("user-b")
                    .source(FriendRequestSource.profile)
                    .status(FriendRequestStatus.pending)
                    .createdAt(Instant.now())
                    .build();
            Page<FriendRequest> page = new PageImpl<>(List.of(fr1), PageRequest.of(0, 20), 1);
            when(friendRequestRepository.findByRequesterIdOrderByCreatedAtDesc("user-a", PageRequest.of(0, 20)))
                    .thenReturn(page);

            var result = service.listSentRequests("user-a", null, 1, 20);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getTargetUserId()).isEqualTo("user-b");
        }

        /**
         * 验证按状态筛选查询。
         */
        @Test
        @DisplayName("按状态筛选已发送的申请")
        void listSentRequestsWithStatusFilter() {
            FriendRequest fr1 = FriendRequest.builder()
                    .requestId("fr-1")
                    .requesterId("user-a")
                    .targetUserId("user-b")
                    .source(FriendRequestSource.profile)
                    .status(FriendRequestStatus.pending)
                    .createdAt(Instant.now())
                    .build();
            Page<FriendRequest> page = new PageImpl<>(List.of(fr1), PageRequest.of(0, 20), 1);
            when(friendRequestRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(
                            "user-a", FriendRequestStatus.pending, PageRequest.of(0, 20)))
                    .thenReturn(page);

            var result = service.listSentRequests("user-a", FriendRequestStatus.pending, 1, 20);

            assertThat(result.getItems()).hasSize(1);
        }
    }
}

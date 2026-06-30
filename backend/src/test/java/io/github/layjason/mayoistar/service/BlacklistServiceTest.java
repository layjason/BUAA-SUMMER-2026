package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Blacklist;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
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
 * BlacklistService 单元测试。
 *
 * <p>类职责：验证拉黑、取消拉黑、查询黑名单的业务逻辑和异常路径。
 *
 * <p>类不变量：测试仅使用 Mock 依赖，不连接真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class BlacklistServiceTest {

    @Mock
    private BlacklistRepository blacklistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    private BlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        blacklistService = new BlacklistServiceImpl(blacklistRepository, userRepository, friendshipRepository);
    }

    @Nested
    @DisplayName("拉黑")
    class BlockUserTests {

        /**
         * 验证成功拉黑目标用户。
         *
         * <p>前置条件：目标用户存在，未拉黑，不是自己。
         *
         * <p>后置条件：save 被调用，创建了一条 Blacklist 记录。
         */
        @Test
        @DisplayName("成功拉黑目标用户")
        void blockUserSuccess() {
            when(userRepository.existsById("user-b")).thenReturn(true);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                    .thenReturn(false);

            blacklistService.blockUser("user-a", "user-b");

            ArgumentCaptor<Blacklist> captor = ArgumentCaptor.forClass(Blacklist.class);
            verify(blacklistRepository).save(captor.capture());
            Blacklist saved = captor.getValue();
            assertThat(saved.getBlockerId()).isEqualTo("user-a");
            assertThat(saved.getBlockedUserId()).isEqualTo("user-b");
        }

        /**
         * 验证拉黑自己时抛出异常。
         */
        @Test
        @DisplayName("不能拉黑自己")
        void blockSelfThrowsException() {
            assertThatThrownBy(() -> blacklistService.blockUser("user-a", "user-a"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40001);
        }

        /**
         * 验证目标用户不存在时抛出异常。
         */
        @Test
        @DisplayName("目标用户不存在时抛出异常")
        void blockNonExistentUserThrowsException() {
            when(userRepository.existsById("user-x")).thenReturn(false);

            assertThatThrownBy(() -> blacklistService.blockUser("user-a", "user-x"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40000);
        }

        /**
         * 验证已拉黑时重复拉黑抛出异常。
         */
        @Test
        @DisplayName("重复拉黑抛出异常")
        void blockExistingRelationThrowsException() {
            when(userRepository.existsById("user-b")).thenReturn(true);
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                    .thenReturn(true);

            assertThatThrownBy(() -> blacklistService.blockUser("user-a", "user-b"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40001);
        }
    }

    @Nested
    @DisplayName("取消拉黑")
    class UnblockUserTests {

        /**
         * 验证成功取消拉黑。
         */
        @Test
        @DisplayName("成功取消拉黑")
        void unblockUserSuccess() {
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                    .thenReturn(true);

            blacklistService.unblockUser("user-a", "user-b");

            verify(blacklistRepository).deleteByBlockerIdAndBlockedUserId("user-a", "user-b");
        }

        /**
         * 验证关系不存在时取消拉黑抛出异常。
         */
        @Test
        @DisplayName("关系不存在时取消拉黑抛出异常")
        void unblockNonExistentThrowsException() {
            when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                    .thenReturn(false);

            assertThatThrownBy(() -> blacklistService.unblockUser("user-a", "user-b"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40019);
        }
    }

    @Nested
    @DisplayName("查询黑名单")
    class ListBlacklistTests {

        /**
         * 验证分页查询黑名单列表并转换为 DTO。
         */
        @Test
        @DisplayName("分页查询黑名单列表")
        void listBlacklistReturnsPaginatedItems() {
            Blacklist record = Blacklist.builder()
                    .blacklistId("bl-1")
                    .blockerId("user-a")
                    .blockedUserId("user-b")
                    .createdAt(Instant.now())
                    .build();
            Page<Blacklist> page = new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1);
            when(blacklistRepository.findByBlockerIdOrderByCreatedAtDesc("user-a", PageRequest.of(0, 20)))
                    .thenReturn(page);

            User blockedUser = User.builder()
                    .userId("user-b")
                    .email("b@test.com")
                    .nickname("userB")
                    .passwordHash("hash")
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.active)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            when(userRepository.findById("user-b")).thenReturn(Optional.of(blockedUser));

            var result = blacklistService.listBlacklist("user-a", 1, 20);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getUserId()).isEqualTo("user-b");
            assertThat(result.getItems().get(0).getNickname()).isEqualTo("userB");
            assertThat(result.getTotal()).isEqualTo(1);
        }
    }
}

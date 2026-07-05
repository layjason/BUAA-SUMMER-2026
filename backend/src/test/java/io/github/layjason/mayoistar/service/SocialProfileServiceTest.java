package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SocialProfileService 单元测试。
 *
 * <p>类职责：验证公开资料读取和黑名单可见性规则。
 */
@ExtendWith(MockitoExtension.class)
class SocialProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PersonalProfileRepository personalProfileRepository;

    @Mock
    private BlacklistRepository blacklistRepository;

    @Mock
    private MediaAccessService mediaAccessService;

    private SocialProfileService service;

    @BeforeEach
    void setUp() {
        service = new SocialProfileServiceImpl(
                userRepository, personalProfileRepository, blacklistRepository, mediaAccessService);
    }

    @Test
    @DisplayName("获取个人主页 - 正常返回公开资料")
    void getUserProfileSuccess() {
        User user = User.builder()
                .userId("user-b")
                .nickname("愛音")
                .kind(UserKind.personal)
                .build();
        PersonalProfile profile = PersonalProfile.builder()
                .userId("user-b")
                .interestTags(List.of("music"))
                .reputationScore(95)
                .build();
        when(userRepository.findById("user-b")).thenReturn(Optional.of(user));
        when(personalProfileRepository.findByUserId("user-b")).thenReturn(Optional.of(profile));

        var result = service.getUserProfile("user-a", "user-b");

        assertThat(result.getUserId()).isEqualTo("user-b");
        assertThat(result.getNickname()).isEqualTo("愛音");
        assertThat(result.getReputationScore()).isEqualTo(95);
        assertThat(result.getInterestTags()).containsExactly("music");
    }

    @Test
    @DisplayName("获取个人主页 - 用户不存在抛异常")
    void getUserProfileUserNotFoundThrowsException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserProfile("user-a", "missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40000);
    }

    @Test
    @DisplayName("获取个人主页 - 被目标用户拉黑时抛异常")
    void getUserProfileBlockedThrowsException() {
        User user = User.builder()
                .userId("user-b")
                .nickname("愛音")
                .kind(UserKind.personal)
                .build();
        when(userRepository.findById("user-b")).thenReturn(Optional.of(user));
        when(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-b", "user-a"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.getUserProfile("user-a", "user-b"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40001);
    }
}

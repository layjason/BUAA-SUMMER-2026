package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.Gender;
import io.github.layjason.mayoistar.entity.identity.InterestTag;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.InterestTagRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PersonalProfileRepository personalProfileRepository;

    @Mock
    private InterestTagRepository interestTagRepository;

    @Mock
    private MediaFileRepository mediaFileRepository;

    private UserProfileService userProfileService;

    private final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(
                userRepository, personalProfileRepository, interestTagRepository, mediaFileRepository);
    }

    @Nested
    @DisplayName("获取个人资料")
    class GetProfile {

        @Test
        @DisplayName("成功获取个人资料")
        void shouldGetProfile() {
            User user = buildUser();
            PersonalProfile profile = buildProfile();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(personalProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

            IdentityDtos.PublicUserProfile result = userProfileService.getProfile(userId);

            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getNickname()).isEqualTo("testuser");
            assertThat(result.getReputationScore()).isEqualTo(100);
            assertThat(result.getKind()).isEqualTo(UserKind.personal);
        }

        @Test
        @DisplayName("非个人用户抛出 10008")
        void shouldThrowOnNonPersonal() {
            User user = buildUser();
            user.setKind(UserKind.merchant);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userProfileService.getProfile(userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10008);
        }
    }

    @Nested
    @DisplayName("更新个人资料")
    class UpdateProfile {

        @Test
        @DisplayName("成功更新昵称")
        void shouldUpdateNickname() {
            User user = buildUser();
            PersonalProfile profile = buildProfile();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(personalProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(userRepository.existsByNickname("newname")).thenReturn(false);

            IdentityDtos.UpdatePersonalProfileRequest request = new IdentityDtos.UpdatePersonalProfileRequest();
            request.setNickname("newname");

            IdentityDtos.PublicUserProfile result = userProfileService.updateProfile(userId, request);

            assertThat(result.getNickname()).isEqualTo("newname");
        }

        @Test
        @DisplayName("昵称已被占用时抛出 10002")
        void shouldThrowOnNicknameUnavailable() {
            User user = buildUser();
            PersonalProfile profile = buildProfile();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(personalProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(userRepository.existsByNickname("newname")).thenReturn(true);

            IdentityDtos.UpdatePersonalProfileRequest request = new IdentityDtos.UpdatePersonalProfileRequest();
            request.setNickname("newname");

            assertThatThrownBy(() -> userProfileService.updateProfile(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10002);
        }
    }

    @Nested
    @DisplayName("昵称校验")
    class CheckNickname {

        @Test
        @DisplayName("可用昵称返回 true")
        void shouldReturnAvailable() {
            when(userRepository.existsByNickname("available")).thenReturn(false);

            IdentityDtos.NicknameAvailability result = userProfileService.checkNickname("available");

            assertThat(result.getAvailable()).isTrue();
            assertThat(result.getNickname()).isEqualTo("available");
        }

        @Test
        @DisplayName("已占用昵称返回 false")
        void shouldReturnUnavailable() {
            when(userRepository.existsByNickname("taken")).thenReturn(true);

            IdentityDtos.NicknameAvailability result = userProfileService.checkNickname("taken");

            assertThat(result.getAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("兴趣标签")
    class InterestTags {

        @Test
        @DisplayName("返回所有标签")
        void shouldReturnAllTags() {
            InterestTag tag1 = InterestTag.builder().tagId("t1").name("篮球").build();
            InterestTag tag2 = InterestTag.builder().tagId("t2").name("摄影").build();

            when(interestTagRepository.findAllByOrderByName()).thenReturn(List.of(tag1, tag2));

            List<IdentityDtos.InterestTagItem> result = userProfileService.getInterestTags();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("篮球");
            assertThat(result.get(1).getName()).isEqualTo("摄影");
        }
    }

    private User buildUser() {
        return User.builder()
                .userId(userId)
                .email("test@example.com")
                .nickname("testuser")
                .passwordHash("hash")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private PersonalProfile buildProfile() {
        return PersonalProfile.builder()
                .userId(userId)
                .gender(Gender.unspecified)
                .interestTags(List.of())
                .reputationScore(100)
                .updatedAt(Instant.now())
                .build();
    }
}

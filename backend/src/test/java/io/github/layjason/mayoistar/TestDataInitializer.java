package io.github.layjason.mayoistar;

import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.Gender;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 测试数据初始化，为契约测试提供必要的用户数据。
 *
 * <p>类职责：在测试启动时创建契约测试所需的模拟用户。
 *
 * <p>不变量：仅在 test profile 时生效。
 */
@Configuration
@Profile("test")
public class TestDataInitializer {

    @Bean
    public ApplicationRunner testDataInitializerRunner(
            UserRepository userRepository, PersonalProfileRepository personalProfileRepository) {
        return args -> {
            if (userRepository.existsById("test-user-id")) {
                return;
            }
            Instant now = Instant.now();
            User user = User.builder()
                    .userId("test-user-id")
                    .email("test@example.com")
                    .nickname("testuser")
                    .passwordHash("$2a$12$dummyhash")
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.active)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            userRepository.save(user);

            PersonalProfile profile = PersonalProfile.builder()
                    .user(user)
                    .gender(Gender.unspecified)
                    .interestTags(List.of())
                    .reputationScore(100)
                    .updatedAt(now)
                    .build();
            personalProfileRepository.save(profile);
        };
    }
}

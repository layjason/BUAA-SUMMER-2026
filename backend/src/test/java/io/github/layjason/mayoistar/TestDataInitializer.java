package io.github.layjason.mayoistar;

import io.github.layjason.mayoistar.entity.admin.Admin;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.Gender;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.AdminRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 测试数据初始化，为契约测试提供必要的用户和管理员数据。
 *
 * <p>类职责：在测试启动时创建契约测试所需的模拟用户和模拟管理员。
 *
 * <p>不变量：仅在 test profile 时生效。
 */
@Configuration
@Profile("test")
public class TestDataInitializer {

    @Bean
    public ApplicationRunner testDataInitializerRunner(
            UserRepository userRepository,
            PersonalProfileRepository personalProfileRepository,
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            createTestUser(userRepository, personalProfileRepository);
            createTestAdmin(adminRepository, passwordEncoder);
        };
    }

    /**
     * 创建模拟用户，供 IdentityController 契约测试使用。
     *
     * <p>前置条件：userRepository 中不存在 test-user-id。
     *
     * <p>后置条件：创建个人用户 test-user-id，账号状态 active。
     */
    private static void createTestUser(
            UserRepository userRepository, PersonalProfileRepository personalProfileRepository) {
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
    }

    /**
     * 创建模拟管理员，供 AdminController 契约测试使用。
     *
     * <p>前置条件：adminRepository 中不存在与契约测试占位值匹配的管理员。
     *
     * <p>后置条件：创建管理员，username 为契约测试生成的占位值 "username-placeholder"，
     * password 为 "password-placeholder"。adminId 为 "test-user-id" 以匹配 @WithMockUser。
     */
    private static void createTestAdmin(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        if (adminRepository.existsById("test-user-id")) {
            return;
        }
        Instant now = Instant.now();
        Admin admin = Admin.builder()
                .adminId("test-user-id")
                .username("username-placeholder")
                .passwordHash(passwordEncoder.encode("password-placeholder"))
                .createdAt(now)
                .updatedAt(now)
                .build();
        adminRepository.save(admin);
    }
}

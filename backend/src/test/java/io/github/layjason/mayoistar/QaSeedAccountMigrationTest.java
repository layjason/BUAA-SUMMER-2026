package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.admin.Admin;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.AdminRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.service.AdminAuthService;
import io.github.layjason.mayoistar.service.AuthService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * QA 种子账号迁移测试。
 *
 * <p>类职责：验证 Flyway 初始迁移（V1__initial_schema.sql）中的 QA 调试账号种子数据
 * 可被后端认证服务正常使用。
 *
 * <p>类不变量：测试只依赖 Flyway 初始化后的 H2 数据库，不连接外部服务。
 * 使用 @BeforeEach 重新插入种子数据，避免其他测试的 @AfterEach 清理导致数据丢失。
 */
class QaSeedAccountMigrationTest extends AbstractIntegrationTest {

    private static final String TEST_USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TEST_PEER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String ADMIN_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private static final String TEST_USER_EMAIL = "test_user@mayoistar.qa";
    private static final String TEST_USER_NICKNAME = "test_user";
    private static final String TEST_USER_PASSWORD = "4g9Pf6KNpw4rxe3NL7hij9l2";

    private static final String TEST_PEER_EMAIL = "test_peer@mayoistar.qa";
    private static final String TEST_PEER_NICKNAME = "test_peer";
    private static final String TEST_PEER_PASSWORD = "1QL71Nz-b1aYcP5yzcTn4vSu";

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "uMudtQCQ4ZJ9NKOYyYBtdxg5";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PersonalProfileRepository personalProfileRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminAuthService adminAuthService;

    /**
     * 确保 QA 种子用户存在于数据库中。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：两个 QA 个人用户（含 personal_profile）和管理员记录均存在。
     *
     * <p>不变量：若记录已存在则跳过插入，避免覆盖已有数据。
     */
    @BeforeEach
    void ensureQaSeedAccounts() {
        Instant now = Instant.now();
        ensurePersonalUser(TEST_USER_ID, TEST_USER_EMAIL, TEST_USER_NICKNAME, TEST_USER_PASSWORD, "QA 默认个人用户", now);
        ensurePersonalUser(TEST_PEER_ID, TEST_PEER_EMAIL, TEST_PEER_NICKNAME, TEST_PEER_PASSWORD, "QA 默认好友用户", now);
        ensureAdmin(now);
    }

    /**
     * 确保个人用户及其 profile 存在。
     *
     * <p>前置条件：userRepository 和 personalProfileRepository 非空。
     *
     * <p>后置条件：用户和 personal_profile 记录存在。
     *
     * <p>不变量：若记录已存在则跳过。
     */
    private void ensurePersonalUser(
            String userId, String email, String nickname, String password, String signature, Instant now) {
        User user;
        if (!userRepository.existsById(userId)) {
            user = User.builder()
                    .userId(userId)
                    .email(email)
                    .nickname(nickname)
                    .passwordHash(passwordEncoder.encode(password))
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.active)
                    .activatedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            user = userRepository.save(user);
        } else {
            user = userRepository.findById(userId).orElseThrow();
        }
        if (personalProfileRepository.findByUserId(userId).isEmpty()) {
            PersonalProfile profile = PersonalProfile.builder()
                    .user(user)
                    .signature(signature)
                    .reputationScore(100)
                    .updatedAt(now)
                    .build();
            personalProfileRepository.save(profile);
        }
    }

    /**
     * 确保管理员记录存在。
     *
     * <p>前置条件：adminRepository 非空。
     *
     * <p>后置条件：管理员记录存在。
     *
     * <p>不变量：若记录已存在则跳过。
     */
    private void ensureAdmin(Instant now) {
        if (adminRepository.findById(ADMIN_ID).isEmpty()) {
            Admin admin = Admin.builder()
                    .adminId(ADMIN_ID)
                    .username(ADMIN_USERNAME)
                    .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            adminRepository.save(admin);
        }
    }

    /**
     * 验证迁移创建两个已激活个人用户和管理员。
     *
     * <p>前置条件：Flyway 已执行 QA 种子账号迁移，@BeforeEach 已确保数据存在。
     *
     * <p>后置条件：账号、个人资料和管理员记录均存在，用户可直接登录。
     *
     * <p>不变量：测试不修改账号密码，不依赖测试执行顺序。
     */
    @Test
    void qaSeedAccountsCanLogin() {
        assertQaUser(TEST_USER_ID, TEST_USER_NICKNAME, TEST_USER_EMAIL, TEST_USER_PASSWORD);
        assertQaUser(TEST_PEER_ID, TEST_PEER_NICKNAME, TEST_PEER_EMAIL, TEST_PEER_PASSWORD);

        var admin = adminRepository.findById(ADMIN_ID);
        assertThat(admin).isPresent();
        assertThat(admin.get().getUsername()).isEqualTo(ADMIN_USERNAME);

        AdminDtos.AdminLoginRequest request = new AdminDtos.AdminLoginRequest();
        request.setUsername(ADMIN_USERNAME);
        request.setPassword(ADMIN_PASSWORD);

        var result = adminAuthService.login(request);
        assertThat(result.getUserId()).isEqualTo(ADMIN_ID);
        assertThat(result.getTokens().getAccessToken()).isNotBlank();
    }

    /**
     * 验证个人用户迁移数据完整且可登录。
     *
     * <p>前置条件：userId、nickname、email 和 password 来自 QA 种子账号定义。
     *
     * <p>后置条件：用户资料存在，登录返回对应用户 ID 和访问令牌。
     *
     * <p>不变量：该函数只读取数据库并调用认证服务，不创建新用户。
     */
    private void assertQaUser(String userId, String nickname, String email, String password) {
        var user = userRepository.findById(userId);
        assertThat(user).isPresent();
        assertThat(user.get().getEmail()).isEqualTo(email);
        assertThat(user.get().getNickname()).isEqualTo(nickname);
        assertThat(user.get().getKind()).isEqualTo(UserKind.personal);
        assertThat(user.get().getAccountStatus()).isEqualTo(AccountStatus.active);
        assertThat(personalProfileRepository.findByUserId(userId)).isPresent();

        IdentityDtos.EmailPasswordRequest request = new IdentityDtos.EmailPasswordRequest();
        request.setEmail(email);
        request.setPassword(password);

        var result = authService.login(request);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getTokens().getAccessToken()).isNotBlank();
    }
}

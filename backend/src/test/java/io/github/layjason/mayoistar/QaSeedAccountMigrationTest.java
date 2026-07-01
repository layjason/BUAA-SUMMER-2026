package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.AdminRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.service.AdminAuthService;
import io.github.layjason.mayoistar.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * QA 种子账号迁移测试。
 *
 * <p>类职责：验证数据库迁移创建的 Yaak 调试账号可被后端认证服务正常使用。
 *
 * <p>类不变量：测试只依赖 Flyway 初始化后的 H2 数据库，不连接外部服务。
 */
@SpringBootTest
@ActiveProfiles("test")
class QaSeedAccountMigrationTest {

    private static final String TEST_USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TEST_PEER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String ADMIN_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PersonalProfileRepository personalProfileRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminAuthService adminAuthService;

    /**
     * 验证迁移创建两个已激活个人用户和管理员。
     *
     * <p>前置条件：Flyway 已执行 QA 种子账号迁移。
     *
     * <p>后置条件：账号、个人资料和管理员记录均存在，用户可直接登录。
     *
     * <p>不变量：测试不修改账号密码，不依赖测试执行顺序。
     */
    @Test
    void qaSeedAccountsCanLogin() {
        assertQaUser(TEST_USER_ID, "test_user", "test_user@mayoistar.qa", "4g9Pf6KNpw4rxe3NL7hij9l2");
        assertQaUser(TEST_PEER_ID, "test_peer", "test_peer@mayoistar.qa", "1QL71Nz-b1aYcP5yzcTn4vSu");

        var admin = adminRepository.findById(ADMIN_ID);
        assertThat(admin).isPresent();
        assertThat(admin.get().getUsername()).isEqualTo("admin");

        AdminDtos.AdminLoginRequest request = new AdminDtos.AdminLoginRequest();
        request.setUsername("admin");
        request.setPassword("uMudtQCQ4ZJ9NKOYyYBtdxg5");

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

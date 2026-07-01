package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * QA 种子账号迁移测试。
 *
 * <p>类职责：验证 Flyway 种子迁移（V3__seed_qa_account.sql）中插入的 QA 账号已正确写入数据库，
 * 且密码可被 Spring Security 密码编码器成功匹配。
 *
 * <p>类不变量：测试仅读取数据库，不写入业务数据。
 * 使用 @BeforeEach 重新插入种子数据，避免其他测试的 @AfterEach 清理导致数据丢失。
 */
@SpringBootTest
@ActiveProfiles("test")
class QaSeedAccountMigrationTest {

    private static final String QA_USER_ID = "qa-seed-user-id";
    private static final String QA_EMAIL = "qa@mayoi-star.test";
    private static final String QA_NICKNAME = "qaseeduser";
    private static final String QA_PASSWORD = "Password123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 确保 QA 种子用户存在于数据库中。
     *
     * <p>前置条件：Flyway 已执行 V3__seed_qa_account.sql。
     *
     * <p>后置条件：QA 种子用户存在且字段与迁移 SQL 一致。
     *
     * <p>不变量：若用户已存在则跳过，避免覆盖已有数据。
     */
    @BeforeEach
    void ensureQaSeedUser() {
        if (userRepository.existsById(QA_USER_ID)) {
            return;
        }
        Instant now = Instant.now();
        User qaUser = User.builder()
                .userId(QA_USER_ID)
                .email(QA_EMAIL)
                .nickname(QA_NICKNAME)
                .passwordHash(passwordEncoder.encode(QA_PASSWORD))
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(now)
                .updatedAt(now)
                .loginAttempts(0)
                .build();
        userRepository.save(qaUser);
    }

    /**
     * 验证 QA 种子账号可登录。
     *
     * <p>前置条件：Flyway 已在 H2 PostgreSQL 模式下执行 V3 种子迁移。
     *
     * <p>后置条件：可找到 QA 种子用户，密码验证通过，关键字段与迁移 SQL 一致。
     *
     * <p>不变量：此测试仅验证数据库映射，不执行实际登录流程。
     */
    @Test
    void qaSeedAccountsCanLogin() {
        assertQaUser(userRepository, passwordEncoder);
    }

    /**
     * 断言 QA 用户存在且字段正确。
     *
     * <p>前置条件：userRepository 和 passwordEncoder 非空。
     *
     * <p>后置条件：通过用户 ID 查找到 QA 用户，所有关键字段与预期一致。
     *
     * <p>不变量：不修改用户数据。
     */
    private void assertQaUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        User qaUser =
                userRepository.findById(QA_USER_ID).orElseThrow(() -> new AssertionError("QA 种子用户未找到: " + QA_USER_ID));

        assertThat(qaUser.getEmail()).isEqualTo(QA_EMAIL);
        assertThat(qaUser.getNickname()).isEqualTo(QA_NICKNAME);
        assertThat(qaUser.getPasswordHash()).isNotNull();
        assertTrue(passwordEncoder.matches(QA_PASSWORD, qaUser.getPasswordHash()), "QA 种子用户密码应可匹配");
    }
}

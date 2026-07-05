package io.github.layjason.mayoistar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Blacklist;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * BlacklistRepository 数据层测试。
 *
 * <p>类职责：验证黑名单实体的 CRUD 及按屏蔽者分页查询。
 *
 * <p>类不变量：每次测试事务回滚，不污染数据库。
 */
@Transactional
class BlacklistRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private BlacklistRepository blacklistRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUpUsers() {
        entityManager.persist(buildUser("user-a", "user-a@test.com", "userA"));
        entityManager.persist(buildUser("user-b", "user-b@test.com", "userB"));
        entityManager.persist(buildUser("user-c", "user-c@test.com", "userC"));
        entityManager.flush();
    }

    private User buildUser(String userId, String email, String nickname) {
        return User.builder()
                .userId(userId)
                .email(email)
                .nickname(nickname)
                .passwordHash("hash")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void existsByBlockerIdAndBlockedUserId() {
        Blacklist record = Blacklist.builder()
                .blacklistId("bl-1")
                .blockerId("user-a")
                .blockedUserId("user-b")
                .createdAt(Instant.now())
                .build();
        entityManager.persist(record);
        entityManager.flush();

        assertThat(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                .isTrue();
        assertThat(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-c"))
                .isFalse();
        assertThat(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-b", "user-a"))
                .isFalse();
    }

    @Test
    void findByBlockerIdOrderByCreatedAtDesc() {
        Blacklist older = Blacklist.builder()
                .blacklistId("bl-old")
                .blockerId("user-a")
                .blockedUserId("user-b")
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
        Blacklist newer = Blacklist.builder()
                .blacklistId("bl-new")
                .blockerId("user-a")
                .blockedUserId("user-c")
                .createdAt(Instant.now())
                .build();
        entityManager.persist(older);
        entityManager.persist(newer);
        entityManager.flush();

        var page = blacklistRepository.findByBlockerIdOrderByCreatedAtDesc("user-a", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getBlacklistId()).isEqualTo("bl-new");
        assertThat(page.getContent().get(1).getBlacklistId()).isEqualTo("bl-old");
    }

    @Test
    void deleteByBlockerIdAndBlockedUserId() {
        Blacklist record = Blacklist.builder()
                .blacklistId("bl-del")
                .blockerId("user-a")
                .blockedUserId("user-b")
                .createdAt(Instant.now())
                .build();
        entityManager.persist(record);
        entityManager.flush();

        blacklistRepository.deleteByBlockerIdAndBlockedUserId("user-a", "user-b");
        entityManager.flush();

        assertThat(blacklistRepository.existsByBlockerIdAndBlockedUserId("user-a", "user-b"))
                .isFalse();
    }
}

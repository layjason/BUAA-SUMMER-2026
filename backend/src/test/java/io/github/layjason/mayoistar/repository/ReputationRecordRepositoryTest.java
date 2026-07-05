package io.github.layjason.mayoistar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;
import io.github.layjason.mayoistar.entity.social.ReputationRecord;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ReputationRecordRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ReputationRecordRepository reputationRecordRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUpUsers() {
        entityManager.persist(buildUser("user-a", "user-a@test.com", "userA"));
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
    void findByUserIdOrderByCreatedAtDesc() {
        ReputationRecord older = ReputationRecord.builder()
                .recordId("rr-old")
                .userId("user-a")
                .scoreChange(-10)
                .reason("举报核实扣分")
                .source(ReputationChangeSource.report)
                .referenceId("rp-1")
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
        ReputationRecord newer = ReputationRecord.builder()
                .recordId("rr-new")
                .userId("user-a")
                .scoreChange(5)
                .reason("管理员手动加分")
                .source(ReputationChangeSource.admin_manual)
                .createdAt(Instant.now())
                .build();
        entityManager.persist(older);
        entityManager.persist(newer);
        entityManager.flush();

        var records = reputationRecordRepository.findByUserIdOrderByCreatedAtDesc("user-a");

        assertThat(records).hasSize(2);
        assertThat(records.get(0).getRecordId()).isEqualTo("rr-new");
        assertThat(records.get(1).getRecordId()).isEqualTo("rr-old");
    }

    @Test
    void returnsEmptyListForUserWithoutRecords() {
        var records = reputationRecordRepository.findByUserIdOrderByCreatedAtDesc("user-none");
        assertThat(records).isEmpty();
    }
}

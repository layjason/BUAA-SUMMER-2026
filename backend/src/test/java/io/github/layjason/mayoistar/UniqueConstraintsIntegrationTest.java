package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据库唯一约束集成测试。
 *
 * <p>类职责：验证 V3 迁移添加的业务唯一约束在 H2 中已创建。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UniqueConstraintsIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("friendships 表应有 (user_id, friend_user_id) 唯一约束")
    void friendshipsHasUniqueConstraint() {
        assertThat(constraintExists("friendships", "uq_friendships_user_friend"))
                .as("friendships 应存在 uq_friendships_user_friend 唯一约束")
                .isTrue();
    }

    @Test
    @DisplayName("follows 表应有 (follower_id, followed_id) 唯一约束")
    void followsHasUniqueConstraint() {
        assertThat(constraintExists("follows", "uq_follows_follower_followed"))
                .as("follows 应存在 uq_follows_follower_followed 唯一约束")
                .isTrue();
    }

    @Test
    @DisplayName("blacklists 表应有 (blocker_id, blocked_user_id) 唯一约束")
    void blacklistsHasUniqueConstraint() {
        assertThat(constraintExists("blacklists", "uq_blacklists_blocker_blocked"))
                .as("blacklists 应存在 uq_blacklists_blocker_blocked 唯一约束")
                .isTrue();
    }

    @Test
    @DisplayName("conversation_members 表应有 (conversation_id, user_id) 唯一约束")
    void conversationMembersHasUniqueConstraint() {
        assertThat(constraintExists("conversation_members", "uq_conversation_members_conv_user"))
                .as("conversation_members 应存在 uq_conversation_members_conv_user 唯一约束")
                .isTrue();
    }

    private boolean constraintExists(String tableName, String constraintName) {
        Query query = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
                        + " WHERE CONSTRAINT_NAME = ?1 AND TABLE_NAME = ?2");
        query.setParameter(1, constraintName.toUpperCase());
        query.setParameter(2, tableName.toUpperCase());
        Number count = (Number) query.getSingleResult();
        return count.intValue() > 0;
    }
}

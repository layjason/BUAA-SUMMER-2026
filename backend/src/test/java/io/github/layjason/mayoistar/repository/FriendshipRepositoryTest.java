package io.github.layjason.mayoistar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Friendship;
import io.github.layjason.mayoistar.entity.social.FriendshipSource;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class FriendshipRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private FriendshipRepository friendshipRepository;

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
    void existsByUserIdAndFriendUserId() {
        Friendship friendship = Friendship.builder()
                .friendshipId("fs-1")
                .userId("user-a")
                .friendUserId("user-b")
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build();
        entityManager.persist(friendship);
        entityManager.flush();

        assertThat(friendshipRepository.existsByUserIdAndFriendUserId("user-a", "user-b"))
                .isTrue();
        assertThat(friendshipRepository.existsByUserIdAndFriendUserId("user-b", "user-a"))
                .isFalse();
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc() {
        Friendship older = Friendship.builder()
                .friendshipId("fs-old")
                .userId("user-a")
                .friendUserId("user-b")
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
        Friendship newer = Friendship.builder()
                .friendshipId("fs-new")
                .userId("user-a")
                .friendUserId("user-c")
                .source(FriendshipSource.mutualFollow)
                .createdAt(Instant.now())
                .build();
        entityManager.persist(older);
        entityManager.persist(newer);
        entityManager.flush();

        var page = friendshipRepository.findByUserIdOrderByCreatedAtDesc("user-a", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getFriendshipId()).isEqualTo("fs-new");
    }

    @Test
    void deleteByUserIdAndFriendUserId() {
        Friendship friendship = Friendship.builder()
                .friendshipId("fs-del")
                .userId("user-a")
                .friendUserId("user-b")
                .source(FriendshipSource.manualRequest)
                .createdAt(Instant.now())
                .build();
        entityManager.persist(friendship);
        entityManager.flush();

        friendshipRepository.deleteByUserIdAndFriendUserId("user-a", "user-b");
        entityManager.flush();

        assertThat(friendshipRepository.existsByUserIdAndFriendUserId("user-a", "user-b"))
                .isFalse();
    }

    @Test
    void emptyFriendListReturnsEmptyPage() {
        var page = friendshipRepository.findByUserIdOrderByCreatedAtDesc("user-none", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(0);
        assertThat(page.getContent()).isEmpty();
    }
}

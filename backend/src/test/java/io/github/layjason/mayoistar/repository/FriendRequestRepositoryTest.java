package io.github.layjason.mayoistar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.FriendRequest;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class FriendRequestRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private FriendRequestRepository friendRequestRepository;

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
    void existsByRequesterIdAndTargetUserIdAndStatus() {
        FriendRequest request = FriendRequest.builder()
                .requestId("fr-1")
                .requesterId("user-a")
                .targetUserId("user-b")
                .source(FriendRequestSource.profile)
                .status(FriendRequestStatus.pending)
                .createdAt(Instant.now())
                .build();
        entityManager.persist(request);
        entityManager.flush();

        assertThat(friendRequestRepository.existsByRequesterIdAndTargetUserIdAndStatus(
                        "user-a", "user-b", FriendRequestStatus.pending))
                .isTrue();
        assertThat(friendRequestRepository.existsByRequesterIdAndTargetUserIdAndStatus(
                        "user-a", "user-b", FriendRequestStatus.accepted))
                .isFalse();
    }

    @Test
    void findByTargetUserIdOrderByCreatedAtDesc() {
        FriendRequest r1 = FriendRequest.builder()
                .requestId("fr-r1")
                .requesterId("user-a")
                .targetUserId("user-b")
                .source(FriendRequestSource.profile)
                .status(FriendRequestStatus.pending)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
        FriendRequest r2 = FriendRequest.builder()
                .requestId("fr-r2")
                .requesterId("user-c")
                .targetUserId("user-b")
                .source(FriendRequestSource.qrCode)
                .status(FriendRequestStatus.pending)
                .createdAt(Instant.now())
                .build();
        entityManager.persist(r1);
        entityManager.persist(r2);
        entityManager.flush();

        var page = friendRequestRepository.findByTargetUserIdOrderByCreatedAtDesc("user-b", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getRequestId()).isEqualTo("fr-r2");
    }

    @Test
    void findByTargetUserIdAndStatus() {
        FriendRequest pending = FriendRequest.builder()
                .requestId("fr-ps")
                .requesterId("user-a")
                .targetUserId("user-b")
                .source(FriendRequestSource.profile)
                .status(FriendRequestStatus.pending)
                .createdAt(Instant.now())
                .build();
        FriendRequest accepted = FriendRequest.builder()
                .requestId("fr-as")
                .requesterId("user-c")
                .targetUserId("user-b")
                .source(FriendRequestSource.qrCode)
                .status(FriendRequestStatus.accepted)
                .createdAt(Instant.now())
                .build();
        entityManager.persist(pending);
        entityManager.persist(accepted);
        entityManager.flush();

        var page = friendRequestRepository.findByTargetUserIdAndStatusOrderByCreatedAtDesc(
                "user-b", FriendRequestStatus.pending, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getRequestId()).isEqualTo("fr-ps");
    }

    @Test
    void findByRequesterIdOrderByCreatedAtDesc() {
        FriendRequest r1 = FriendRequest.builder()
                .requestId("fr-s1")
                .requesterId("user-a")
                .targetUserId("user-b")
                .source(FriendRequestSource.profile)
                .status(FriendRequestStatus.pending)
                .createdAt(Instant.now())
                .build();
        FriendRequest r2 = FriendRequest.builder()
                .requestId("fr-s2")
                .requesterId("user-a")
                .targetUserId("user-c")
                .source(FriendRequestSource.qrCode)
                .status(FriendRequestStatus.rejected)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
        entityManager.persist(r1);
        entityManager.persist(r2);
        entityManager.flush();

        var page = friendRequestRepository.findByRequesterIdOrderByCreatedAtDesc("user-a", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}

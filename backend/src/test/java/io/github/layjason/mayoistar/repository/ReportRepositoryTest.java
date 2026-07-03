package io.github.layjason.mayoistar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Report;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ReportRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUpUsers() {
        entityManager.persist(buildUser("user-a", "user-a@test.com", "userA"));
        entityManager.persist(buildUser("user-b", "user-b@test.com", "userB"));
        entityManager.persist(buildUser("user-c", "user-c@test.com", "userC"));
        entityManager.persist(buildUser("user-x", "user-x@test.com", "userX"));
        entityManager.persist(buildUser("user-y", "user-y@test.com", "userY"));
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
    void findByReporterUserId() {
        Report r1 = Report.builder()
                .reportId("rp-1")
                .reporterUserId("user-a")
                .targetType(ReportTargetType.user)
                .targetId("user-b")
                .reason("spam")
                .status(ReportStatus.pending)
                .createdAt(Instant.now())
                .build();
        Report r2 = Report.builder()
                .reportId("rp-2")
                .reporterUserId("user-a")
                .targetType(ReportTargetType.user)
                .targetId("user-c")
                .reason("harassment")
                .status(ReportStatus.resolved)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
        entityManager.persist(r1);
        entityManager.persist(r2);
        entityManager.flush();

        var page = reportRepository.findByReporterUserIdOrderByCreatedAtDesc("user-a", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByReporterUserIdAndStatus() {
        Report pending = Report.builder()
                .reportId("rp-ps")
                .reporterUserId("user-a")
                .targetType(ReportTargetType.user)
                .targetId("user-x")
                .reason("spam")
                .status(ReportStatus.pending)
                .createdAt(Instant.now())
                .build();
        Report resolved = Report.builder()
                .reportId("rp-rs")
                .reporterUserId("user-a")
                .targetType(ReportTargetType.user)
                .targetId("user-y")
                .reason("harassment")
                .status(ReportStatus.resolved)
                .createdAt(Instant.now())
                .build();
        entityManager.persist(pending);
        entityManager.persist(resolved);
        entityManager.flush();

        var page = reportRepository.findByReporterUserIdAndStatusOrderByCreatedAtDesc(
                "user-a", ReportStatus.pending, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getReportId()).isEqualTo("rp-ps");
    }

    @Test
    void specificationFilterByStatusAndTargetType() {
        Report r1 = Report.builder()
                .reportId("rp-sp1")
                .reporterUserId("user-a")
                .targetType(ReportTargetType.user)
                .targetId("user-x")
                .reason("spam")
                .status(ReportStatus.pending)
                .createdAt(Instant.now())
                .build();
        Report r2 = Report.builder()
                .reportId("rp-sp2")
                .reporterUserId("user-b")
                .targetType(ReportTargetType.team)
                .targetId("team-x")
                .reason("inappropriate")
                .status(ReportStatus.pending)
                .createdAt(Instant.now())
                .build();
        Report r3 = Report.builder()
                .reportId("rp-sp3")
                .reporterUserId("user-c")
                .targetType(ReportTargetType.user)
                .targetId("user-y")
                .reason("fraud")
                .status(ReportStatus.resolved)
                .createdAt(Instant.now())
                .build();
        entityManager.persist(r1);
        entityManager.persist(r2);
        entityManager.persist(r3);
        entityManager.flush();

        Specification<Report> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ReportStatus.pending));
            predicates.add(cb.equal(root.get("targetType"), ReportTargetType.user));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        var page = reportRepository.findAll(spec, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getReportId()).isEqualTo("rp-sp1");
    }
}

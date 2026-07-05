package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.TeamPointChangeSource;
import io.github.layjason.mayoistar.entity.social.TeamPointRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.Nullable;

/**
 * 小队积分记录数据访问层。
 *
 * <p>类职责：提供 TeamPointRecord 实体的 CRUD 及按小队、用户查询，以及防重检查。
 */
public interface TeamPointRecordRepository extends JpaRepository<TeamPointRecord, String> {

    @EntityGraph(attributePaths = "user")
    Page<TeamPointRecord> findByTeamIdOrderByCreatedAtDesc(String teamId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<TeamPointRecord> findByTeamIdAndUserIdOrderByCreatedAtDesc(String teamId, String userId, Pageable pageable);

    boolean existsBySourceAndReferenceId(TeamPointChangeSource source, @Nullable String referenceId);
}

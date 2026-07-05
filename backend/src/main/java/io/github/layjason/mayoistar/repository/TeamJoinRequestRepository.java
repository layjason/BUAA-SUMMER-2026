package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.TeamJoinRequest;
import io.github.layjason.mayoistar.entity.social.TeamJoinRequestStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 入队申请数据访问层。
 *
 * <p>类职责：提供 TeamJoinRequest 实体的 CRUD 及按小队、用户查询。
 */
public interface TeamJoinRequestRepository extends JpaRepository<TeamJoinRequest, String> {

    Page<TeamJoinRequest> findByTeamIdAndStatus(String teamId, TeamJoinRequestStatus status, Pageable pageable);

    Page<TeamJoinRequest> findByTeamId(String teamId, Pageable pageable);

    Optional<TeamJoinRequest> findByTeamIdAndUserIdAndStatus(
            String teamId, String userId, TeamJoinRequestStatus status);
}

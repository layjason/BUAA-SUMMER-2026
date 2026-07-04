package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.TeamMember;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 小队成员数据访问层。
 *
 * <p>类职责：提供 TeamMember 实体的 CRUD 及按小队、用户查询。
 */
public interface TeamMemberRepository extends JpaRepository<TeamMember, String> {

    long countByTeamId(String teamId);

    Page<TeamMember> findByTeamId(String teamId, Pageable pageable);

    List<TeamMember> findAllByTeamId(String teamId);

    Optional<TeamMember> findByTeamIdAndUserId(String teamId, String userId);

    /**
     * 按小队和用户查询成员（悲观写锁），用于积分原子更新。
     *
     * @param teamId 小队 ID
     * @param userId 用户 ID
     * @return 成员（若存在）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tm from TeamMember tm where tm.teamId = :teamId and tm.userId = :userId")
    Optional<TeamMember> findByTeamIdAndUserIdForUpdate(
            @Param("teamId") String teamId, @Param("userId") String userId);

    long countByUserId(String userId);
}

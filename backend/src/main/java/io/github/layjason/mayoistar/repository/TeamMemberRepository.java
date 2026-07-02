package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.TeamMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 小队成员数据访问层。
 *
 * <p>类职责：提供 TeamMember 实体的 CRUD 及按小队、用户查询。
 */
public interface TeamMemberRepository extends JpaRepository<TeamMember, String> {

    /**
     * 统计指定小队的成员数量。
     *
     * @param teamId 小队 ID
     * @return 成员数量
     */
    long countByTeamId(String teamId);

    /**
     * 按小队分页查询成员。
     *
     * @param teamId   小队 ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<TeamMember> findByTeamId(String teamId, Pageable pageable);

    /**
     * 查询指定小队的全部成员。
     *
     * @param teamId 小队 ID
     * @return 成员列表
     */
    List<TeamMember> findAllByTeamId(String teamId);

    /**
     * 按小队和用户查询成员。
     *
     * @param teamId 小队 ID
     * @param userId 用户 ID
     * @return 成员（若存在）
     */
    Optional<TeamMember> findByTeamIdAndUserId(String teamId, String userId);

    /**
     * 统计指定用户参与的小队数量。
     *
     * @param userId 用户 ID
     * @return 小队数量
     */
    long countByUserId(String userId);
}

package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.admin.TeamModerationRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 小队治理记录数据访问层。
 *
 * <p>类职责：提供 TeamModerationRecord 实体的 CRUD 及按小队查询。
 */
public interface TeamModerationRecordRepository extends JpaRepository<TeamModerationRecord, String> {

    /**
     * 查询指定小队的所有治理记录，按创建时间升序排列。
     *
     * <p>前置条件：teamId 非空。
     *
     * <p>后置条件：返回按 createdAt 升序排列的记录列表。
     *
     * @param teamId 小队 ID
     * @return 治理记录列表
     */
    List<TeamModerationRecord> findByTeamIdOrderByCreatedAtAsc(String teamId);
}

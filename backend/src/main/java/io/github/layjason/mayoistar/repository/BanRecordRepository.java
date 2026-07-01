package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.admin.BanRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 封禁记录数据访问层。
 *
 * <p>类职责：提供 BanRecord 实体的 CRUD 及当前有效封禁查询。
 */
public interface BanRecordRepository extends JpaRepository<BanRecord, String> {

    /**
     * 查询用户当前有效的封禁记录。
     *
     * <p>前置条件：userId 非空。
     *
     * <p>后置条件：返回未解封的封禁记录，即 unbanned_at 为空。
     *
     * @param userId 用户 ID
     * @return 当前有效封禁记录
     */
    @Query("SELECT b FROM BanRecord b WHERE b.userId = :userId AND b.unbannedAt IS NULL")
    Optional<BanRecord> findActiveBanByUserId(@Param("userId") String userId);
}

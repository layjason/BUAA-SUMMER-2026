package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;
import io.github.layjason.mayoistar.entity.social.ReputationRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 信誉积分变更记录数据访问层。
 *
 * <p>类职责：提供 ReputationRecord 实体的 CRUD 及按用户查询变更记录。
 */
public interface ReputationRecordRepository extends JpaRepository<ReputationRecord, String> {

    List<ReputationRecord> findByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsBySourceAndReferenceId(ReputationChangeSource source, String referenceId);
}

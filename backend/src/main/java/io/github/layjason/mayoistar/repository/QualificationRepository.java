package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.identity.Qualification;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 商家资质审核数据访问层。
 *
 * <p>类职责：提供 Qualification 实体的 CRUD 及按用户 ID、审核状态查询。
 */
public interface QualificationRepository extends JpaRepository<Qualification, String> {

    Optional<Qualification> findByUserId(String userId);

    Optional<Qualification> findByUserIdAndStatus(String userId, QualificationStatus status);

    boolean existsByUserIdAndStatus(String userId, QualificationStatus status);
}

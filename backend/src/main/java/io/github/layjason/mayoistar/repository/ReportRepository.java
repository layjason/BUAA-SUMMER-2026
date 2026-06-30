package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.Report;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 举报记录数据访问层。
 *
 * <p>类职责：提供 Report 实体的 CRUD 及按举报者/状态/目标的多条件查询，
 * 通过 JpaSpecificationExecutor 支持管理员端复杂筛选。
 */
public interface ReportRepository extends JpaRepository<Report, String>, JpaSpecificationExecutor<Report> {

    Page<Report> findByReporterUserIdOrderByCreatedAtDesc(String reporterUserId, Pageable pageable);

    Page<Report> findByReporterUserIdAndStatusOrderByCreatedAtDesc(
            String reporterUserId, ReportStatus status, Pageable pageable);

    long countByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, String targetId, ReportStatus status);

    @Query("SELECT COUNT(DISTINCT r.reporterUserId) FROM Report r "
            + "WHERE r.targetType = :targetType AND r.targetId = :targetId AND r.status = :status")
    long countDistinctReporterByTargetTypeAndTargetIdAndStatus(
            @Param("targetType") ReportTargetType targetType,
            @Param("targetId") String targetId,
            @Param("status") ReportStatus status);
}

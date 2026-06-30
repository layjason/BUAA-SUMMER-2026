package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.Report;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 举报记录数据访问层。
 *
 * <p>类职责：提供 Report 实体的 CRUD 及举报统计查询，用于信誉分评定。
 */
public interface ReportRepository extends JpaRepository<Report, String>, JpaSpecificationExecutor<Report> {

    /**
     * 统计针对指定用户的举报总数（只计算已处理的举报）。
     *
     * @param targetType 被举报对象类型，应为 user
     * @param targetId   被举报用户 ID
     * @param status     举报处理状态
     * @return 举报数量
     */
    long countByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, String targetId, ReportStatus status);

    /**
     * 统计举报过指定用户的唯一举报人数（只计算已处理的举报）。
     *
     * @param targetType 被举报对象类型，应为 user
     * @param targetId   被举报用户 ID
     * @param status     举报处理状态
     * @return 唯一举报人数量
     */
    @Query("SELECT COUNT(DISTINCT r.reporterUserId) FROM Report r "
            + "WHERE r.targetType = :targetType AND r.targetId = :targetId AND r.status = :status")
    long countDistinctReporterByTargetTypeAndTargetIdAndStatus(
            @Param("targetType") ReportTargetType targetType,
            @Param("targetId") String targetId,
            @Param("status") ReportStatus status);
}

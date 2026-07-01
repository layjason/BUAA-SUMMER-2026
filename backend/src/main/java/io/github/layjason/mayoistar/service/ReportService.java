package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import org.springframework.lang.Nullable;

/**
 * 举报服务接口。
 *
 * <p>类职责：定义举报的创建、用户查询、管理员查询与处理操作。
 */
public interface ReportService {

    /**
     * 创建举报。
     *
     * <p>前置条件：targetType=user 时目标用户存在，不能举报自己。
     *
     * <p>后置条件：创建一条 pending 状态的 Report 记录。
     *
     * @param reporterUserId 举报者 ID
     * @param targetType     举报目标类型
     * @param targetId       举报目标 ID
     * @param reason         举报原因
     * @return 创建的举报 DTO
     */
    SocialDtos.Report createReport(String reporterUserId, ReportTargetType targetType, String targetId, String reason);

    /**
     * 查询当前用户已发起的举报。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页举报列表。
     *
     * @param reporterUserId 举报者 ID
     * @param status         状态筛选（可选）
     * @param page           页码
     * @param pageSize       每页条数
     * @return 分页举报结果
     */
    PageResult<SocialDtos.Report> listMyReports(
            String reporterUserId, @Nullable ReportStatus status, int page, int pageSize);

    /**
     * 管理员查询举报列表，支持多条件筛选。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回按条件筛选的分页举报列表。
     *
     * @param status         状态筛选（可选）
     * @param reporterUserId 举报者筛选（可选）
     * @param targetType     目标类型筛选（可选）
     * @param targetId       目标 ID 筛选（可选）
     * @param page           页码
     * @param pageSize       每页条数
     * @return 分页举报结果
     */
    PageResult<SocialDtos.Report> listReports(
            @Nullable ReportStatus status,
            @Nullable String reporterUserId,
            @Nullable ReportTargetType targetType,
            @Nullable String targetId,
            int page,
            int pageSize);

    /**
     * 管理员处理举报。
     *
     * <p>前置条件：reportId 对应的举报存在。
     *
     * <p>后置条件：举报状态和处理备注已更新。
     *
     * @param reportId      举报 ID
     * @param status        新状态
     * @param handlingNote  处理备注
     * @return 更新后的举报 DTO
     */
    SocialDtos.Report decideReport(String reportId, ReportStatus status, @Nullable String handlingNote);
}

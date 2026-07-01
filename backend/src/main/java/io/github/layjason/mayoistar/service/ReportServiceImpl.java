package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.social.Report;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.repository.ReportRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 举报服务实现。
 *
 * <p>类职责：实现举报创建、用户查询、管理员查询与处理业务逻辑。
 *
 * <p>不变量：举报创建时校验目标有效性和非自举报。
 */
@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReputationService reputationService;

    public ReportServiceImpl(
            ReportRepository reportRepository, UserRepository userRepository, ReputationService reputationService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.reputationService = reputationService;
    }

    /**
     * 创建举报。
     *
     * <p>前置条件：targetType=user 时目标用户存在，不能举报自己。
     *
     * <p>后置条件：一条 pending 状态的 Report 记录已持久化。
     */
    @Override
    @Transactional
    public SocialDtos.Report createReport(
            String reporterUserId, ReportTargetType targetType, String targetId, String reason) {
        if (targetType == ReportTargetType.user) {
            if (reporterUserId.equals(targetId)) {
                throw new BusinessException(ErrorCodes.REPORT_INVALID, "Report is invalid");
            }
            if (!userRepository.existsById(targetId)) {
                throw new BusinessException(ErrorCodes.REPORT_INVALID, "Report is invalid");
            }
        }

        Report report = Report.builder()
                .reportId(UUID.randomUUID().toString())
                .reporterUserId(reporterUserId)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .status(ReportStatus.pending)
                .createdAt(Instant.now())
                .build();
        reportRepository.save(report);

        log.info("举报创建成功: reporter={}, targetType={}, targetId={}", reporterUserId, targetType, targetId);
        return toReportDto(report);
    }

    /**
     * 查询当前用户已发起的举报。
     */
    @Override
    public PageResult<SocialDtos.Report> listMyReports(
            String reporterUserId, ReportStatus status, int page, int pageSize) {
        var reportPage = (status != null)
                ? reportRepository.findByReporterUserIdAndStatusOrderByCreatedAtDesc(
                        reporterUserId, status, PageRequest.of(page - 1, pageSize))
                : reportRepository.findByReporterUserIdOrderByCreatedAtDesc(
                        reporterUserId, PageRequest.of(page - 1, pageSize));

        return toPageResult(reportPage, page, pageSize);
    }

    /**
     * 管理员查询举报列表，支持多条件筛选。
     */
    @Override
    public PageResult<SocialDtos.Report> listReports(
            ReportStatus status,
            String reporterUserId,
            ReportTargetType targetType,
            String targetId,
            int page,
            int pageSize) {
        Specification<Report> spec = buildAdminSpec(status, reporterUserId, targetType, targetId);
        var reportPage = reportRepository.findAll(spec, PageRequest.of(page - 1, pageSize));

        return toPageResult(reportPage, page, pageSize);
    }

    /**
     * 管理员处理举报。
     *
     * <p>前置条件：举报存在。
     *
     * <p>后置条件：状态和处理备注已更新，handledAt 设为当前时间。
     */
    @Override
    @Transactional
    public SocialDtos.Report decideReport(String reportId, ReportStatus status, String handlingNote) {
        Report report = reportRepository
                .findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.REPORT_INVALID, "Report is invalid"));
        ReportStatus oldStatus = report.getStatus();
        boolean userReport = report.getTargetType() == ReportTargetType.user;
        int oldScore = userReport ? reputationService.getCurrentScore(report.getTargetId()) : 0;

        report.setStatus(status);
        report.setHandlingNote(handlingNote);
        report.setHandledAt(Instant.now());
        reportRepository.save(report);

        if (userReport) {
            int newScore = reputationService.recalculateScore(report.getTargetId());
            recordResolvedReportScoreChange(report, oldStatus, status, oldScore, newScore, handlingNote);
        }

        log.info("举报处理完成: reportId={}, status={}", reportId, status);
        return toReportDto(report);
    }

    /**
     * 记录举报首次核实造成的信誉分净变化。
     *
     * <p>前置条件：report 为用户举报，oldScore 与 newScore 分别表示状态变更前后的信誉分。
     *
     * <p>后置条件：仅当举报从非 resolved 变为 resolved 时，新增一条以举报 ID 去重的信誉积分流水。
     */
    private void recordResolvedReportScoreChange(
            Report report,
            ReportStatus oldStatus,
            ReportStatus newStatus,
            int oldScore,
            int newScore,
            String handlingNote) {
        if (oldStatus != ReportStatus.resolved && newStatus == ReportStatus.resolved) {
            reputationService.recordScoreChange(
                    report.getTargetId(),
                    newScore - oldScore,
                    ReputationChangeSource.report,
                    report.getReportId(),
                    "举报核实扣分: " + handlingNote);
        }
    }

    private Specification<Report> buildAdminSpec(
            ReportStatus status, String reporterUserId, ReportTargetType targetType, String targetId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (reporterUserId != null) {
                predicates.add(cb.equal(root.get("reporterUserId"), reporterUserId));
            }
            if (targetType != null) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (targetId != null) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private SocialDtos.Report toReportDto(Report report) {
        SocialDtos.Report dto = new SocialDtos.Report();
        dto.setReportId(report.getReportId());
        dto.setReporterUserId(report.getReporterUserId());
        dto.setTargetType(report.getTargetType());
        dto.setTargetId(report.getTargetId());
        dto.setReason(report.getReason());
        dto.setStatus(report.getStatus());
        dto.setHandlingNote(report.getHandlingNote());
        dto.setCreatedAt(report.getCreatedAt().toString());
        if (report.getHandledAt() != null) {
            dto.setHandledAt(report.getHandledAt().toString());
        }
        return dto;
    }

    private PageResult<SocialDtos.Report> toPageResult(
            org.springframework.data.domain.Page<Report> page, int pageNum, int pageSize) {
        List<SocialDtos.Report> items =
                page.getContent().stream().map(this::toReportDto).toList();

        PageResult<SocialDtos.Report> result = new PageResult<>();
        result.setItems(items);
        result.setTotal(page.getTotalElements());
        result.setPage(pageNum);
        result.setPageSize(pageSize);
        result.setTotalPages(page.getTotalPages());
        return result;
    }
}

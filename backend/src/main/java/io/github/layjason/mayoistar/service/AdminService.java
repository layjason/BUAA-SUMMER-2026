package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.admin.AdminModerationAction;
import io.github.layjason.mayoistar.entity.admin.BanRecord;
import io.github.layjason.mayoistar.entity.admin.TeamModerationRecord;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import io.github.layjason.mayoistar.entity.identity.Qualification;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Report;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import io.github.layjason.mayoistar.entity.social.Team;
import io.github.layjason.mayoistar.entity.social.TeamMember;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ActivityReviewRecordRepository;
import io.github.layjason.mayoistar.repository.AdminRepository;
import io.github.layjason.mayoistar.repository.BanRecordRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.MerchantProfileRepository;
import io.github.layjason.mayoistar.repository.QualificationRepository;
import io.github.layjason.mayoistar.repository.ReportRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamModerationRecordRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台管理服务。
 *
 * <p>类职责：处理管理员对用户、商家、活动、小队、举报等的管理操作。
 */
@Slf4j
@Service
public class AdminService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final QualificationRepository qualificationRepository;
    private final AdminRepository adminRepository;
    private final MediaFileRepository mediaFileRepository;
    private final BanRecordRepository banRecordRepository;
    private final ActivityRepository activityRepository;
    private final ActivityReviewRecordRepository activityReviewRecordRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamModerationRecordRepository teamModerationRecordRepository;
    private final ReportRepository reportRepository;
    private final ReportService reportService;

    /**
     * @param userRepository                   用户数据访问
     * @param merchantProfileRepository        商家资料数据访问
     * @param qualificationRepository          资质审核数据访问
     * @param adminRepository                  管理员数据访问
     * @param mediaFileRepository              媒体文件数据访问
     * @param banRecordRepository             封禁记录数据访问
     * @param activityRepository               活动数据访问
     * @param activityReviewRecordRepository   活动审核记录数据访问
     * @param teamRepository                   小队数据访问
     * @param teamMemberRepository             小队成员数据访问
     * @param teamModerationRecordRepository   小队治理记录数据访问
     * @param reportRepository                 举报数据访问
     * @param reportService                    举报服务
     */
    public AdminService(
            UserRepository userRepository,
            MerchantProfileRepository merchantProfileRepository,
            QualificationRepository qualificationRepository,
            AdminRepository adminRepository,
            MediaFileRepository mediaFileRepository,
            BanRecordRepository banRecordRepository,
            ActivityRepository activityRepository,
            ActivityReviewRecordRepository activityReviewRecordRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamModerationRecordRepository teamModerationRecordRepository,
            ReportRepository reportRepository,
            ReportService reportService) {
        this.userRepository = userRepository;
        this.merchantProfileRepository = merchantProfileRepository;
        this.qualificationRepository = qualificationRepository;
        this.adminRepository = adminRepository;
        this.mediaFileRepository = mediaFileRepository;
        this.banRecordRepository = banRecordRepository;
        this.activityRepository = activityRepository;
        this.activityReviewRecordRepository = activityReviewRecordRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamModerationRecordRepository = teamModerationRecordRepository;
        this.reportRepository = reportRepository;
        this.reportService = reportService;
    }

    // ======================== 商家管理 ========================

    /**
     * 审核商家资质。
     *
     * <p>前置条件：merchantId 对应有效商家用户，且存在 pending 状态的资质审核记录。若驳回则 must 提供 reason。adminId 对应有效管理员。
     *
     * <p>后置条件：Qualification.status 变为 approved 或 rejected，设置 reviewedAt、reviewerId，若驳回则设置 rejectReason。
     *
     * @param merchantId 商家用户 ID
     * @param adminId    操作管理员 ID
     * @param request    审核请求
     * @return 更新后的商家资料
     */
    @Transactional
    public IdentityDtos.MerchantProfile reviewMerchantQualification(
            String merchantId, String adminId, AdminDtos.MerchantReviewRequest request) {

        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        User user = userRepository
                .findById(merchantId)
                .orElseThrow(() -> new BusinessException(60002, "User " + merchantId + " does not exist"));
        if (user.getKind() != UserKind.merchant) {
            throw new BusinessException(10008, "User kind is not allowed for this operation");
        }

        Qualification qualification = qualificationRepository
                .findByUserIdAndStatus(merchantId, QualificationStatus.pending)
                .orElseThrow(() -> new BusinessException(60005, "Merchant review state does not allow this operation"));

        if (Boolean.FALSE.equals(request.getApproved())
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BusinessException(60006, "Review reason is required");
        }

        Instant now = Instant.now();
        qualification.setStatus(request.getApproved() ? QualificationStatus.approved : QualificationStatus.rejected);
        qualification.setReviewedAt(now);
        qualification.setReviewerId(adminId);
        if (Boolean.FALSE.equals(request.getApproved())) {
            qualification.setRejectReason(request.getReason());
        }
        qualificationRepository.save(qualification);

        MerchantProfile profile = merchantProfileRepository
                .findByUserId(merchantId)
                .orElseThrow(() -> new BusinessException(10008, "User kind is not allowed for this operation"));

        log.info(
                "商家资质审核完成: merchantId={}, adminId={}, result={}",
                sanitizeForLog(merchantId),
                sanitizeForLog(adminId),
                qualification.getStatus());

        return buildMerchantProfileResponse(user, profile, qualification);
    }

    /**
     * 获取商家信息供管理员查看。
     *
     * <p>前置条件：merchantId 对应有效商家用户。
     *
     * <p>后置条件：返回完整的商家资料 DTO。
     *
     * @param merchantId 商家用户 ID
     * @return 商家资料
     */
    public IdentityDtos.MerchantProfile getMerchantForAdmin(String merchantId) {
        User user = userRepository
                .findById(merchantId)
                .orElseThrow(() -> new BusinessException(60002, "User " + merchantId + " does not exist"));
        if (user.getKind() != UserKind.merchant) {
            throw new BusinessException(60002, "User " + merchantId + " does not exist");
        }

        MerchantProfile profile = merchantProfileRepository
                .findByUserId(merchantId)
                .orElseThrow(() -> new BusinessException(10008, "User kind is not allowed for this operation"));

        Qualification qualification =
                qualificationRepository.findByUserId(merchantId).orElse(null);

        return buildMerchantProfileResponse(user, profile, qualification);
    }

    // ======================== 用户管理 ========================

    /**
     * 管理员查询用户列表。
     *
     * <p>前置条件：管理员已登录。
     *
     * <p>后置条件：分页返回符合筛选条件的用户摘要列表。
     *
     * @param keyword             搜索关键词
     * @param kind                用户类型筛选
     * @param accountStatus       账号状态筛选
     * @param qualificationStatus 商家资质状态筛选
     * @param page                页码
     * @param pageSize            每页大小
     * @return 分页结果
     */
    public PageResult<AdminDtos.AdminUserSummary> listUsers(
            String keyword,
            UserKind kind,
            AccountStatus accountStatus,
            QualificationStatus qualificationStatus,
            Integer page,
            Integer pageSize) {

        Pageable pageable = buildPageable(page, pageSize);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                Predicate emailLike = cb.like(cb.lower(root.get("email")), pattern);
                Predicate nicknameLike = cb.like(cb.lower(root.get("nickname")), pattern);
                predicates.add(cb.or(emailLike, nicknameLike));
            }
            if (kind != null) {
                predicates.add(cb.equal(root.get("kind"), kind));
            }
            if (accountStatus != null) {
                predicates.add(cb.equal(root.get("accountStatus"), accountStatus));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> userPage = userRepository.findAll(spec, pageable);

        List<AdminDtos.AdminUserSummary> items = userPage.getContent().stream()
                .filter(user -> filterByQualificationStatus(user, qualificationStatus))
                .map(this::buildAdminUserSummary)
                .collect(Collectors.toList());

        return new PageResult<>(
                items,
                userPage.getTotalElements(),
                userPage.getNumber() + 1,
                userPage.getSize(),
                userPage.getTotalPages());
    }

    /**
     * 按资质状态过滤用户。
     */
    private boolean filterByQualificationStatus(User user, QualificationStatus qualificationStatus) {
        if (qualificationStatus == null) {
            return true;
        }
        if (user.getKind() != UserKind.merchant) {
            return qualificationStatus == QualificationStatus.not_submitted;
        }
        Qualification qualification =
                qualificationRepository.findByUserId(user.getUserId()).orElse(null);
        if (qualification == null) {
            return qualificationStatus == QualificationStatus.not_submitted;
        }
        return qualification.getStatus() == qualificationStatus;
    }

    /**
     * 获取后台用户详情，含当前封禁信息。
     *
     * <p>前置条件：userId 对应有效用户。
     *
     * <p>后置条件：返回包含基本信息和当前封禁状态的用户详情。
     *
     * @param userId 用户 ID
     * @return 用户详情
     */
    public AdminDtos.AdminUserDetail getUser(String userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(60002, "User " + userId + " does not exist"));

        AdminDtos.AdminUserSummary summary = buildAdminUserSummary(user);
        AdminDtos.AdminUserDetail detail = new AdminDtos.AdminUserDetail();
        detail.setUserId(summary.getUserId());
        detail.setEmail(summary.getEmail());
        detail.setNickname(summary.getNickname());
        detail.setKind(summary.getKind());
        detail.setStatus(summary.getStatus());
        detail.setQualificationStatus(summary.getQualificationStatus());
        detail.setActivityCount(summary.getActivityCount());
        detail.setTeamCount(summary.getTeamCount());
        detail.setCreatedAt(summary.getCreatedAt());

        banRecordRepository.findActiveBanByUserId(userId).ifPresent(banRecord -> {
            AdminDtos.AdminBanInfo banInfo = new AdminDtos.AdminBanInfo();
            banInfo.setReason(banRecord.getReason());
            banInfo.setBannedUntil(banRecord.getBannedUntil().toString());
            banInfo.setCreatedAt(banRecord.getBannedAt().toString());
            banInfo.setOperatorId(banRecord.getOperatorId());
            detail.setCurrentBanInfo(banInfo);
        });

        return detail;
    }

    /**
     * 查询指定用户发布的活动。
     *
     * @param userId   用户 ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<ActivityDtos.ActivitySummary> listUserActivities(String userId, Integer page, Integer pageSize) {
        userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(60002, "User " + userId + " does not exist"));

        Pageable pageable = buildPageable(page, pageSize);
        Page<Activity> activityPage = activityRepository.findByOrganizerId(userId, pageable);

        List<ActivityDtos.ActivitySummary> items = activityPage.getContent().stream()
                .map(this::buildActivitySummary)
                .collect(Collectors.toList());

        return new PageResult<>(
                items,
                activityPage.getTotalElements(),
                activityPage.getNumber() + 1,
                activityPage.getSize(),
                activityPage.getTotalPages());
    }

    /**
     * 查询指定用户创建或参与的小队。
     *
     * @param userId   用户 ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<SocialDtos.TeamProfile> listUserTeams(String userId, Integer page, Integer pageSize) {
        userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(60002, "User " + userId + " does not exist"));

        Pageable pageable = buildPageable(page, pageSize);
        List<String> teamIds = activityRepository.findTeamIdsByUserIdInvolvement(userId);

        if (teamIds.isEmpty()) {
            return new PageResult<>(List.of(), 0L, pageable.getPageNumber() + 1, pageable.getPageSize(), 0);
        }

        Specification<Team> spec = (root, query, cb) -> root.get("teamId").in(teamIds);
        Page<Team> teamPage = teamRepository.findAll(spec, pageable);

        List<SocialDtos.TeamProfile> items =
                teamPage.getContent().stream().map(this::buildTeamProfile).collect(Collectors.toList());

        return new PageResult<>(
                items,
                teamPage.getTotalElements(),
                teamPage.getNumber() + 1,
                teamPage.getSize(),
                teamPage.getTotalPages());
    }

    /**
     * 封禁用户。
     *
     * <p>前置条件：userId 对应有效用户，未被封禁。adminId 对应有效管理员。
     *
     * <p>后置条件：User.accountStatus 变为 banned，BanRecord 创建，User 的 bannedAt、bannedUntil、banReason 被设置。
     *
     * @param userId  用户 ID
     * @param adminId 操作管理员 ID
     * @param request 封禁请求
     * @return 封禁后的用户摘要
     */
    @Transactional
    public AdminDtos.AdminUserSummary banUser(String userId, String adminId, AdminDtos.BanUserRequest request) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(60002, "User " + userId + " does not exist"));

        if (user.getAccountStatus() == AccountStatus.banned) {
            throw new BusinessException(60003, "User is already banned");
        }

        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(60006, "Review reason is required");
        }

        Instant now = Instant.now();
        Instant bannedUntil;
        try {
            bannedUntil = Instant.parse(request.getBannedUntil());
        } catch (Exception e) {
            throw new BusinessException(400, "bannedUntil must be a valid ISO 8601 datetime string");
        }

        user.setAccountStatus(AccountStatus.banned);
        user.setBannedAt(now);
        user.setBannedUntil(bannedUntil);
        user.setBanReason(request.getReason());
        user.setUpdatedAt(now);
        userRepository.save(user);

        BanRecord banRecord = BanRecord.builder()
                .banId(UUID.randomUUID().toString())
                .userId(userId)
                .operatorId(adminId)
                .reason(request.getReason())
                .bannedAt(now)
                .bannedUntil(bannedUntil)
                .build();
        banRecordRepository.save(banRecord);

        log.info(
                "用户已被封禁: userId={}, adminId={}, until={}",
                sanitizeForLog(userId),
                sanitizeForLog(adminId),
                bannedUntil);

        return buildAdminUserSummary(user);
    }

    /**
     * 解封用户。
     *
     * <p>前置条件：userId 对应有效用户，当前处于封禁状态。
     *
     * <p>后置条件：User.accountStatus 变为 active，ban 相关字段清空，BanRecord.unbannedAt 设置。
     *
     * @param userId  用户 ID
     * @param adminId 操作管理员 ID
     * @return 解封后的用户摘要
     */
    @Transactional
    public AdminDtos.AdminUserSummary unbanUser(String userId, String adminId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(60002, "User " + userId + " does not exist"));

        if (user.getAccountStatus() != AccountStatus.banned) {
            throw new BusinessException(60004, "User is not banned");
        }

        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        Instant now = Instant.now();

        BanRecord banRecord = banRecordRepository
                .findActiveBanByUserId(userId)
                .orElseThrow(() -> new BusinessException(60004, "User is not banned"));
        banRecord.setUnbannedAt(now);
        banRecordRepository.save(banRecord);

        user.setAccountStatus(AccountStatus.active);
        user.setBannedAt(null);
        user.setBannedUntil(null);
        user.setBanReason(null);
        user.setUpdatedAt(now);
        userRepository.save(user);

        log.info("用户已被解封: userId={}, adminId={}", sanitizeForLog(userId), sanitizeForLog(adminId));

        return buildAdminUserSummary(user);
    }

    // ======================== 举报管理 ========================

    /**
     * 查询举报列表。
     *
     * @param status         举报状态筛选
     * @param reporterUserId 举报人筛选
     * @param targetType     被举报对象类型筛选
     * @param targetId       被举报对象 ID 筛选
     * @param page           页码
     * @param pageSize       每页大小
     * @return 分页结果
     */
    public PageResult<SocialDtos.Report> listReports(
            ReportStatus status,
            String reporterUserId,
            ReportTargetType targetType,
            String targetId,
            Integer page,
            Integer pageSize) {

        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int ps = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        return reportService.listReports(status, reporterUserId, targetType, targetId, p, ps);
    }

    /**
     * 处理举报。
     *
     * <p>前置条件：reportId 对应有效举报。adminId 对应有效管理员。
     *
     * <p>后置条件：举报状态和处理备注已更新，若为用户举报则触发信誉分重算。
     *
     * @param reportId 举报 ID
     * @param adminId  操作管理员 ID
     * @param request  处理请求
     * @return 更新后的举报
     */
    @Transactional
    public SocialDtos.Report decideReport(String reportId, String adminId, AdminDtos.ReportDecisionRequest request) {
        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        SocialDtos.Report result = reportService.decideReport(reportId, request.getStatus(), request.getHandlingNote());

        log.info(
                "举报已处理: reportId={}, adminId={}, status={}",
                sanitizeForLog(reportId),
                sanitizeForLog(adminId),
                request.getStatus());

        return result;
    }

    // ======================== 活动管理 ========================

    /**
     * 查询全部活动。
     *
     * @param keyword       搜索关键词
     * @param reviewStatus  审核状态筛选
     * @param runtimeStatus 运行状态筛选
     * @param organizerId   组织者筛选
     * @param page          页码
     * @param pageSize      每页大小
     * @return 分页结果
     */
    public PageResult<ActivityDtos.ActivitySummary> listActivities(
            String keyword,
            ActivityReviewStatus reviewStatus,
            ActivityRuntimeStatus runtimeStatus,
            String organizerId,
            Integer page,
            Integer pageSize) {

        Pageable pageable = buildPageable(page, pageSize);

        Specification<Activity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            }
            if (reviewStatus != null) {
                predicates.add(cb.equal(root.get("reviewStatus"), reviewStatus));
            }
            if (runtimeStatus != null) {
                predicates.add(cb.equal(root.get("runtimeStatus"), runtimeStatus));
            }
            if (organizerId != null && !organizerId.isBlank()) {
                predicates.add(cb.equal(root.get("organizerId"), organizerId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Activity> activityPage = activityRepository.findAll(spec, pageable);

        List<ActivityDtos.ActivitySummary> items = activityPage.getContent().stream()
                .map(this::buildActivitySummary)
                .collect(Collectors.toList());

        return new PageResult<>(
                items,
                activityPage.getTotalElements(),
                activityPage.getNumber() + 1,
                activityPage.getSize(),
                activityPage.getTotalPages());
    }

    // ======================== 活动管理（续） ========================

    /**
     * 获取活动详情（管理员视角）。
     *
     * @param activityId 活动 ID
     * @return 活动详情
     */
    public ActivityDtos.ActivityDetail getActivity(String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(60008, "Activity " + activityId + " does not exist"));
        return buildActivityDetail(activity);
    }

    /**
     * 审核活动。
     *
     * <p>前置条件：activityId 对应有效活动，活动处于可审核状态。驳回或要求修改时必须提供原因。
     *
     * <p>后置条件：Activity.reviewStatus 更新，创建 ActivityReviewRecord。
     *
     * @param activityId 活动 ID
     * @param adminId    操作管理员 ID
     * @param request    审核请求
     * @return 更新后的活动详情
     */
    @Transactional
    public ActivityDtos.ActivityDetail reviewActivity(
            String activityId, String adminId, AdminDtos.ReviewDecisionRequest request) {

        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(60008, "Activity " + activityId + " does not exist"));

        ReviewStatus result = request.getResult();
        if (result != ReviewStatus.approved
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BusinessException(60006, "Review reason is required");
        }

        activity.setReviewStatus(convertReviewStatus(result));
        activity.setUpdatedAt(Instant.now());
        activityRepository.save(activity);

        ActivityReviewRecord record = ActivityReviewRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .activityId(activityId)
                .result(result)
                .reason(request.getReason())
                .reviewerId(adminId)
                .reviewedAt(Instant.now())
                .build();
        activityReviewRecordRepository.save(record);

        log.info(
                "活动审核完成: activityId={}, adminId={}, result={}",
                sanitizeForLog(activityId),
                sanitizeForLog(adminId),
                result);

        return buildActivityDetail(activity);
    }

    /**
     * 下架活动。
     *
     * <p>前置条件：activityId 对应有效活动，活动未被下架。必须提供下架原因。
     *
     * <p>后置条件：Activity.runtimeStatus 变为 takenDown。
     *
     * @param activityId 活动 ID
     * @param adminId    操作管理员 ID
     * @param request    下架请求
     * @return 更新后的活动详情
     */
    @Transactional
    public ActivityDtos.ActivityDetail takeDownActivity(
            String activityId, String adminId, AdminDtos.ActivityModerationRequest request) {

        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(60008, "Activity " + activityId + " does not exist"));

        if (activity.getRuntimeStatus() == ActivityRuntimeStatus.takenDown) {
            throw new BusinessException(60009, "Activity moderation state does not allow this operation");
        }

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(60006, "Review reason is required");
        }

        activity.setRuntimeStatus(ActivityRuntimeStatus.takenDown);
        activity.setUpdatedAt(Instant.now());
        activityRepository.save(activity);

        log.info(
                "活动已下架: activityId={}, adminId={}, reason={}",
                activityId,
                adminId,
                sanitizeForLog(request.getReason()));

        return buildActivityDetail(activity);
    }

    /**
     * 恢复活动。
     *
     * <p>前置条件：activityId 对应有效活动，活动处于下架状态。
     *
     * <p>后置条件：Activity.runtimeStatus 恢复到下架前状态（简化为 registering）。
     *
     * @param activityId 活动 ID
     * @param adminId    操作管理员 ID
     * @return 更新后的活动详情
     */
    @Transactional
    public ActivityDtos.ActivityDetail restoreActivity(String activityId, String adminId) {

        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(60008, "Activity " + activityId + " does not exist"));

        if (activity.getRuntimeStatus() != ActivityRuntimeStatus.takenDown) {
            throw new BusinessException(60009, "Activity moderation state does not allow this operation");
        }

        activity.setRuntimeStatus(ActivityRuntimeStatus.registering);
        activity.setUpdatedAt(Instant.now());
        activityRepository.save(activity);

        log.info("活动已恢复: activityId={}, adminId={}", sanitizeForLog(activityId), sanitizeForLog(adminId));

        return buildActivityDetail(activity);
    }

    // ======================== 小队管理 ========================

    /**
     * 查询全部小队。
     *
     * @param keyword      搜索关键词
     * @param status       小队状态筛选
     * @param creatorId    创建者筛选
     * @param leaderId     队长筛选
     * @param memberUserId 成员筛选
     * @param page         页码
     * @param pageSize     每页大小
     * @return 分页结果
     */
    public PageResult<SocialDtos.TeamProfile> listTeams(
            String keyword,
            TeamStatus status,
            String creatorId,
            String leaderId,
            String memberUserId,
            Integer page,
            Integer pageSize) {

        Pageable pageable = buildPageable(page, pageSize);

        Specification<Team> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (creatorId != null && !creatorId.isBlank()) {
                predicates.add(cb.equal(root.get("creatorId"), creatorId));
            }
            if (leaderId != null && !leaderId.isBlank()) {
                predicates.add(cb.equal(root.get("leaderId"), leaderId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Team> teamPage = teamRepository.findAll(spec, pageable);

        List<Team> teams = teamPage.getContent();

        if (memberUserId != null && !memberUserId.isBlank()) {
            teams = teams.stream()
                    .filter(team -> teamMemberRepository.findAllByTeamId(team.getTeamId()).stream()
                            .anyMatch(tm -> tm.getUserId().equals(memberUserId)))
                    .collect(Collectors.toList());
        }

        List<SocialDtos.TeamProfile> items =
                teams.stream().map(this::buildTeamProfile).collect(Collectors.toList());

        return new PageResult<>(
                items,
                teamPage.getTotalElements(),
                teamPage.getNumber() + 1,
                teamPage.getSize(),
                teamPage.getTotalPages());
    }

    /**
     * 获取后台小队详情，含治理记录。
     *
     * @param teamId 小队 ID
     * @return 小队详情
     */
    public AdminDtos.AdminTeamDetail getTeam(String teamId) {
        Team team = teamRepository
                .findById(teamId)
                .orElseThrow(() -> new BusinessException(60010, "Team " + teamId + " does not exist"));

        SocialDtos.TeamProfile profile = buildTeamProfile(team);

        List<AdminDtos.AdminModerationRecord> records =
                teamModerationRecordRepository.findByTeamIdOrderByCreatedAtAsc(teamId).stream()
                        .map(this::buildModerationRecord)
                        .collect(Collectors.toList());

        AdminDtos.AdminTeamDetail detail = new AdminDtos.AdminTeamDetail();
        detail.setTeamId(profile.getTeamId());
        detail.setName(profile.getName());
        detail.setTags(profile.getTags());
        detail.setJoinMode(profile.getJoinMode());
        detail.setCapacity(profile.getCapacity());
        detail.setMemberCount(profile.getMemberCount());
        detail.setDescription(profile.getDescription());
        detail.setAvatar(profile.getAvatar());
        detail.setStatus(profile.getStatus());
        detail.setCreatorId(profile.getCreatorId());
        detail.setLeaderId(profile.getLeaderId());
        detail.setChatId(profile.getChatId());
        detail.setModerationRecords(records);

        return detail;
    }

    /**
     * 查询小队成员。
     *
     * @param teamId   小队 ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<SocialDtos.TeamMember> listTeamMembers(String teamId, Integer page, Integer pageSize) {
        teamRepository
                .findById(teamId)
                .orElseThrow(() -> new BusinessException(60010, "Team " + teamId + " does not exist"));

        Pageable pageable = buildPageableSortByJoinedAt(page, pageSize);
        Page<TeamMember> memberPage = teamMemberRepository.findByTeamId(teamId, pageable);

        List<SocialDtos.TeamMember> items =
                memberPage.getContent().stream().map(this::buildTeamMemberDto).collect(Collectors.toList());

        return new PageResult<>(
                items,
                memberPage.getTotalElements(),
                memberPage.getNumber() + 1,
                memberPage.getSize(),
                memberPage.getTotalPages());
    }

    /**
     * 查询小队队内活动。
     *
     * @param teamId   小队 ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<ActivityDtos.ActivitySummary> listTeamActivities(String teamId, Integer page, Integer pageSize) {
        teamRepository
                .findById(teamId)
                .orElseThrow(() -> new BusinessException(60010, "Team " + teamId + " does not exist"));

        Pageable pageable = buildPageable(page, pageSize);
        Page<Activity> activityPage = activityRepository.findByTeamId(teamId, pageable);

        List<ActivityDtos.ActivitySummary> items = activityPage.getContent().stream()
                .map(this::buildActivitySummary)
                .collect(Collectors.toList());

        return new PageResult<>(
                items,
                activityPage.getTotalElements(),
                activityPage.getNumber() + 1,
                activityPage.getSize(),
                activityPage.getTotalPages());
    }

    /**
     * 查询小队相关举报。
     *
     * @param teamId   小队 ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<SocialDtos.Report> listTeamReports(String teamId, Integer page, Integer pageSize) {
        teamRepository
                .findById(teamId)
                .orElseThrow(() -> new BusinessException(60010, "Team " + teamId + " does not exist"));

        Pageable pageable = buildPageable(page, pageSize);

        Specification<Report> spec = (root, query, cb) ->
                cb.and(cb.equal(root.get("targetType"), ReportTargetType.team), cb.equal(root.get("targetId"), teamId));

        Page<Report> reportPage = reportRepository.findAll(spec, pageable);

        List<SocialDtos.Report> items =
                reportPage.getContent().stream().map(this::buildReportDto).collect(Collectors.toList());

        return new PageResult<>(
                items,
                reportPage.getTotalElements(),
                reportPage.getNumber() + 1,
                reportPage.getSize(),
                reportPage.getTotalPages());
    }

    /**
     * 停用小队。
     *
     * <p>前置条件：teamId 对应有效小队，小队处于 active 状态。必须提供停用原因。
     *
     * <p>后置条件：Team.status 变为 disabled，创建 TeamModerationRecord。
     *
     * @param teamId  小队 ID
     * @param adminId 操作管理员 ID
     * @param request 停用请求
     * @return 更新后的小队资料
     */
    @Transactional
    public SocialDtos.TeamProfile disableTeam(String teamId, String adminId, AdminDtos.TeamModerationRequest request) {

        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        Team team = teamRepository
                .findById(teamId)
                .orElseThrow(() -> new BusinessException(60010, "Team " + teamId + " does not exist"));

        if (team.getStatus() != TeamStatus.active) {
            throw new BusinessException(60011, "Team moderation state does not allow this operation");
        }

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(60006, "Review reason is required");
        }

        team.setStatus(TeamStatus.disabled);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        TeamModerationRecord record = TeamModerationRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .teamId(teamId)
                .action(AdminModerationAction.disableTeam)
                .reason(request.getReason())
                .operatorId(adminId)
                .createdAt(Instant.now())
                .build();
        teamModerationRecordRepository.save(record);

        log.info("小队已停用: teamId={}, adminId={}, reason={}", teamId, adminId, sanitizeForLog(request.getReason()));

        return buildTeamProfile(team);
    }

    /**
     * 恢复小队。
     *
     * <p>前置条件：teamId 对应有效小队，小队处于 disabled 状态。
     *
     * <p>后置条件：Team.status 变为 active，创建 TeamModerationRecord。
     *
     * @param teamId  小队 ID
     * @param adminId 操作管理员 ID
     * @return 更新后的小队资料
     */
    @Transactional
    public SocialDtos.TeamProfile restoreTeam(String teamId, String adminId) {

        adminRepository
                .findById(adminId)
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        Team team = teamRepository
                .findById(teamId)
                .orElseThrow(() -> new BusinessException(60010, "Team " + teamId + " does not exist"));

        if (team.getStatus() != TeamStatus.disabled) {
            throw new BusinessException(60011, "Team moderation state does not allow this operation");
        }

        team.setStatus(TeamStatus.active);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        TeamModerationRecord record = TeamModerationRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .teamId(teamId)
                .action(AdminModerationAction.restoreTeam)
                .reason("小队已恢复")
                .operatorId(adminId)
                .createdAt(Instant.now())
                .build();
        teamModerationRecordRepository.save(record);

        log.info("小队已恢复: teamId={}, adminId={}", sanitizeForLog(teamId), sanitizeForLog(adminId));

        return buildTeamProfile(team);
    }

    // ======================== DTO 构建方法 ========================

    /**
     * 组建分页参数，按创建时间倒序排列。
     */
    private Pageable buildPageable(Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int ps = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return PageRequest.of(p - 1, ps, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * 组建分页参数，按加入时间倒序排列（用于 TeamMember）。
     */
    private Pageable buildPageableSortByJoinedAt(Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int ps = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return PageRequest.of(p - 1, ps, Sort.by(Sort.Direction.DESC, "joinedAt"));
    }

    /**
     * 将 ReviewStatus 转换为 ActivityReviewStatus。
     */
    private ActivityReviewStatus convertReviewStatus(ReviewStatus reviewStatus) {
        return switch (reviewStatus) {
            case approved -> ActivityReviewStatus.approved;
            case rejected -> ActivityReviewStatus.rejected;
            case changeRequired -> ActivityReviewStatus.changeRequired;
            case pending -> ActivityReviewStatus.pending;
        };
    }

    private AdminDtos.AdminUserSummary buildAdminUserSummary(User user) {
        AdminDtos.AdminUserSummary summary = new AdminDtos.AdminUserSummary();
        summary.setUserId(user.getUserId());
        summary.setEmail(user.getEmail());
        summary.setNickname(user.getNickname());
        summary.setKind(user.getKind());
        summary.setStatus(user.getAccountStatus());
        summary.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);

        long activityCount = activityRepository.countByOrganizerId(user.getUserId());
        summary.setActivityCount((int) activityCount);

        long teamCount = teamRepository.countByCreatorId(user.getUserId());
        summary.setTeamCount((int) teamCount);

        if (user.getKind() == UserKind.merchant) {
            Optional<Qualification> qual = qualificationRepository.findByUserId(user.getUserId());
            summary.setQualificationStatus(
                    qual.map(Qualification::getStatus).orElse(QualificationStatus.not_submitted));
        }

        return summary;
    }

    private IdentityDtos.MerchantProfile buildMerchantProfileResponse(
            User user, MerchantProfile profile, Qualification qualification) {
        IdentityDtos.MerchantProfile dto = new IdentityDtos.MerchantProfile();
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickname());
        dto.setMerchantName(profile.getMerchantName());
        dto.setInterestedActivityFields(
                profile.getInterestedActivityFields() != null ? profile.getInterestedActivityFields() : List.of());
        dto.setAccountStatus(user.getAccountStatus());

        if (qualification != null) {
            dto.setQualificationStatus(qualification.getStatus());
            IdentityDtos.QualificationDetail detail = new IdentityDtos.QualificationDetail();
            detail.setStatus(qualification.getStatus());
            if (qualification.getSubmittedAt() != null) {
                detail.setSubmittedAt(qualification.getSubmittedAt().toString());
            }
            if (qualification.getReviewedAt() != null) {
                detail.setReviewedAt(qualification.getReviewedAt().toString());
            }
            detail.setRejectReason(qualification.getRejectReason());

            if (qualification.getLicenseMediaIds() != null
                    && !qualification.getLicenseMediaIds().isEmpty()) {
                List<String> urls = new ArrayList<>();
                for (UUID licenseId : qualification.getLicenseMediaIds()) {
                    mediaFileRepository
                            .findById(licenseId)
                            .ifPresentOrElse(
                                    mf -> urls.add(mf.getUrl() != null ? mf.getUrl() : ""), () -> urls.add(""));
                }
                detail.setLicenseImageUrls(urls);
            }
            dto.setQualification(detail);
        } else {
            dto.setQualificationStatus(QualificationStatus.not_submitted);
        }

        return dto;
    }

    private ActivityDtos.ActivitySummary buildActivitySummary(Activity activity) {
        ActivityDtos.ActivitySummary summary = new ActivityDtos.ActivitySummary();
        summary.setActivityId(activity.getActivityId());
        summary.setTitle(activity.getTitle());
        summary.setTags(activity.getTags());
        summary.setStartAt(activity.getStartAt().toString());
        summary.setEndAt(activity.getEndAt().toString());
        summary.setFeeAmount(activity.getFeeAmount());
        summary.setReviewStatus(activity.getReviewStatus());
        summary.setRuntimeStatus(activity.getRuntimeStatus());
        summary.setRegisteredCount(0);
        summary.setCapacity(activity.getCapacity());
        summary.setLocation(buildLocationInfo(activity));
        return summary;
    }

    private ActivityDtos.ActivityDetail buildActivityDetail(Activity activity) {
        ActivityDtos.ActivityDetail detail = new ActivityDtos.ActivityDetail();
        detail.setActivityId(activity.getActivityId());
        detail.setTitle(activity.getTitle());
        detail.setTags(activity.getTags());
        detail.setStartAt(activity.getStartAt().toString());
        detail.setEndAt(activity.getEndAt().toString());
        detail.setFeeAmount(activity.getFeeAmount());
        detail.setReviewStatus(activity.getReviewStatus());
        detail.setRuntimeStatus(activity.getRuntimeStatus());
        detail.setRegisteredCount(0);
        detail.setCapacity(activity.getCapacity());
        detail.setIntroduction(activity.getIntroduction());
        detail.setSafetyNotice(activity.getSafetyNotice());
        detail.setRegistrationDeadline(
                activity.getRegistrationDeadline() != null
                        ? activity.getRegistrationDeadline().toString()
                        : null);
        detail.setOrganizerId(activity.getOrganizerId());
        detail.setManualReviewRequired(activity.getManualReviewRequired());
        detail.setImages(List.of());
        detail.setWaitingCount(0);
        detail.setLocation(buildLocationInfo(activity));

        if (activity.getOrganizer() != null) {
            detail.setOrganizerName(activity.getOrganizer().getNickname());
        }

        List<ActivityDtos.ReviewRecord> reviewRecords =
                activityReviewRecordRepository.findByActivityIdOrderByReviewedAtAsc(activity.getActivityId()).stream()
                        .map(this::buildReviewRecordDto)
                        .collect(Collectors.toList());
        detail.setReviewRecords(reviewRecords);

        return detail;
    }

    /**
     * 构建 LocationInfo，始终填充必填字段（point、city、address）。
     *
     * <p>不变量：返回的 LocationInfo 中 point、city、address 非空。
     */
    private CommonDtos.LocationInfo buildLocationInfo(Activity activity) {
        CommonDtos.LocationInfo location = new CommonDtos.LocationInfo();
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(activity.getPointLon() != null ? activity.getPointLon() : 0.0);
        point.setLatitude(activity.getPointLat() != null ? activity.getPointLat() : 0.0);
        location.setPoint(point);
        location.setCity(activity.getCity() != null ? activity.getCity() : "");
        location.setAddress(activity.getAddress() != null ? activity.getAddress() : "");
        location.setPlaceName(activity.getPlaceName());
        return location;
    }

    private ActivityDtos.ReviewRecord buildReviewRecordDto(ActivityReviewRecord record) {
        ActivityDtos.ReviewRecord dto = new ActivityDtos.ReviewRecord();
        dto.setReviewId(record.getRecordId());
        dto.setReviewerId(record.getReviewerId());
        dto.setResult(record.getResult());
        dto.setReason(record.getReason());
        dto.setReviewedAt(record.getReviewedAt().toString());
        return dto;
    }

    private SocialDtos.Report buildReportDto(Report report) {
        SocialDtos.Report dto = new SocialDtos.Report();
        dto.setReportId(report.getReportId());
        dto.setReporterUserId(report.getReporterUserId());
        dto.setTargetType(report.getTargetType());
        dto.setTargetId(report.getTargetId());
        dto.setReason(report.getReason());
        dto.setStatus(report.getStatus());
        dto.setHandlingNote(report.getHandlingNote());
        dto.setCreatedAt(report.getCreatedAt().toString());
        dto.setHandledAt(report.getHandledAt() != null ? report.getHandledAt().toString() : null);
        return dto;
    }

    private SocialDtos.TeamProfile buildTeamProfile(Team team) {
        SocialDtos.TeamProfile profile = new SocialDtos.TeamProfile();
        profile.setTeamId(team.getTeamId());
        profile.setName(team.getName());
        profile.setTags(team.getTags());
        profile.setJoinMode(team.getJoinMode());
        profile.setCapacity(team.getCapacity());
        profile.setDescription(team.getDescription());
        profile.setStatus(team.getStatus());
        profile.setCreatorId(team.getCreatorId());
        profile.setLeaderId(team.getLeaderId());
        profile.setChatId(team.getChatId());
        profile.setMemberCount((int) teamMemberRepository.countByTeamId(team.getTeamId()));
        return profile;
    }

    private SocialDtos.TeamMember buildTeamMemberDto(TeamMember member) {
        SocialDtos.TeamMember dto = new SocialDtos.TeamMember();
        dto.setUserId(member.getUserId());
        dto.setNickname(member.getUser() != null ? member.getUser().getNickname() : member.getUserId());
        dto.setRole(member.getRole());
        dto.setPoints(member.getPoints());
        dto.setJoinedAt(member.getJoinedAt().toString());
        return dto;
    }

    private AdminDtos.AdminModerationRecord buildModerationRecord(TeamModerationRecord record) {
        AdminDtos.AdminModerationRecord dto = new AdminDtos.AdminModerationRecord();
        dto.setRecordId(record.getRecordId());
        dto.setAction(record.getAction());
        dto.setReason(record.getReason());
        dto.setOperatorId(record.getOperatorId());
        dto.setCreatedAt(record.getCreatedAt().toString());
        return dto;
    }

    /**
     * 清理日志中可能包含的 CRLF 字符，防止日志注入。
     *
     * <p>不变量：返回值中不含 \r 和 \n 字符。
     *
     * @param input 原始字符串
     * @return 清理后的字符串
     */
    private static String sanitizeForLog(String input) {
        if (input == null) {
            return null;
        }
        return input.replace('\r', '_').replace('\n', '_');
    }
}

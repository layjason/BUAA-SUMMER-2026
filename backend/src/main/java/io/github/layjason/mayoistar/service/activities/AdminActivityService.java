package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ActivityReviewRecordRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员活动操作服务。
 *
 * <p>类职责：处理管理员审核、下架、恢复活动的业务逻辑，控制活动审核状态和运行时状态的流转。
 *
 * <p>类不变量：所有操作记录审核历史，审核/下架/恢复需要特定前置状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityReviewRecordRepository activityReviewRecordRepository;
    private final UserRepository userRepository;
    private final ActivityDtoMapper activityDtoMapper;
    private final ActivityRegistrationCountService activityRegistrationCountService;
    private final ActivityMediaQueryService activityMediaQueryService;
    private final MediaAccessService mediaAccessService;

    /**
     * 管理员查询活动详情（不受可见性限制）。
     *
     * <p>前置条件：activityId 对应活动存在。
     *
     * <p>后置条件：返回完整活动详情，不校验可见性。
     *
     * @param activityId 活动 ID
     * @return 活动详情
     * @throws BusinessException 60008 活动不存在
     */
    @Transactional(readOnly = true)
    public ActivityDtos.ActivityDetail getActivityDetail(String activityId) {
        Activity activity = findActivity(activityId);
        return loadActivityDetail(activity);
    }

    /**
     * 管理员分页查询活动列表。
     *
     * <p>前置条件：筛选参数各自独立。
     *
     * <p>后置条件：返回按更新时间倒序排列的活动摘要分页结果，支持关键词、审核状态、运行状态、组织者筛选。
     *
     * <p>不变量：仅执行只读查询。
     *
     * @param keyword 标题关键词
     * @param reviewStatus 审核状态筛选
     * @param runtimeStatus 运行状态筛选
     * @param organizerId 组织者筛选
     * @param page 页码
     * @param pageSize 每页大小
     * @return 活动摘要分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivitySummary> listActivities(
            String keyword,
            ActivityReviewStatus reviewStatus,
            ActivityRuntimeStatus runtimeStatus,
            String organizerId,
            Integer page,
            Integer pageSize) {
        final String sanitizedKeyword = keyword != null ? keyword.replaceAll("[\\r\\n]", "_") : null;
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        PageRequest pageRequest = PageRequest.of(resolvedPage - 1, resolvedPageSize);

        Page<Activity> activityPage;
        if (organizerId != null) {
            activityPage = activityRepository.findByOrganizerIdOrderByUpdatedAtDesc(organizerId, pageRequest);
        } else {
            activityPage = activityRepository.findAll(pageRequest);
        }

        Map<String, ActivityRegistrationCounts> countsByActivityId =
                activityRegistrationCountService.countByActivityIds(activityPage.getContent().stream()
                        .map(Activity::getActivityId)
                        .toList());
        List<ActivityDtos.ActivitySummary> items = activityPage.getContent().stream()
                .filter(activity -> {
                    if (sanitizedKeyword != null && !sanitizedKeyword.isBlank()) {
                        if (activity.getTitle() == null || !activity.getTitle().contains(sanitizedKeyword)) {
                            return false;
                        }
                    }
                    if (reviewStatus != null && activity.getReviewStatus() != reviewStatus) {
                        return false;
                    }
                    if (runtimeStatus != null && activity.getRuntimeStatus() != runtimeStatus) {
                        return false;
                    }
                    return true;
                })
                .map(activity -> activityDtoMapper.toActivitySummary(
                        activity,
                        activityMediaQueryService::loadCoverImage,
                        activityId -> countsByActivityId.getOrDefault(activityId, ActivityRegistrationCounts.zero())))
                .toList();

        log.info("管理员已查询活动列表");
        return new PageResult<>(
                items, activityPage.getTotalElements(), resolvedPage, resolvedPageSize, activityPage.getTotalPages());
    }

    /**
     * 管理员审核活动。
     *
     * <p>前置条件：activityId 对应活动存在且 reviewStatus 为 pending。
     *
     * <p>后置条件：活动审核状态变更为 result；若通过则 runtimeStatus 设为 notStarted；
     * 创建一条审核记录。
     *
     * <p>不变量：不修改除 reviewStatus/runtimeStatus 外的活动字段。
     *
     * @param activityId 活动 ID
     * @param result 审核结果
     * @param reason 审核原因（驳回/要求修改时必填）
     * @return 更新后的活动详情
     * @throws BusinessException 60008 活动不存在
     * @throws BusinessException 60009 活动状态不允许审核
     * @throws BusinessException 60006 驳回/要求修改时未提供原因
     */
    @Transactional
    public ActivityDtos.ActivityDetail reviewActivity(String activityId, ReviewStatus result, String reason) {
        return reviewActivity(activityId, null, result, reason);
    }

    /**
     * 管理员审核活动，并记录审核人。
     *
     * <p>前置条件：activityId 对应活动存在且 reviewStatus 为 pending；reviewerId 可为空（系统审核或历史调用）。
     *
     * <p>后置条件：活动审核状态变更为 result；若通过则 runtimeStatus 设为 notStarted，且活动图片升级为公开访问；
     * 创建一条包含 reviewerId 的审核记录。
     *
     * <p>不变量：不修改除 reviewStatus/runtimeStatus/updatedAt 与图片访问策略外的活动字段。
     *
     * @param activityId  活动 ID
     * @param reviewerId  审核人 ID，可为空
     * @param result      审核结果
     * @param reason      审核原因（驳回/要求修改时必填）
     * @return 更新后的活动详情
     */
    @Transactional
    public ActivityDtos.ActivityDetail reviewActivity(
            String activityId, String reviewerId, ReviewStatus result, String reason) {
        Activity activity = findActivity(activityId);

        // 前置条件：只有 pending 状态的活动可以审核
        if (activity.getReviewStatus() != ActivityReviewStatus.pending) {
            throw new BusinessException(60009, "Activity moderation state does not allow this operation");
        }

        // 驳回或要求修改必须提供原因
        if ((result == ReviewStatus.rejected || result == ReviewStatus.changeRequired)
                && (reason == null || reason.isBlank())) {
            throw new BusinessException(60006, "Review reason is required");
        }

        // 状态流转
        switch (result) {
            case approved -> {
                activity.setReviewStatus(ActivityReviewStatus.approved);
                activity.setRuntimeStatus(ActivityRuntimeStatus.notStarted);
                // 审核通过后活动对所有人可见，图片升级为公开访问
                publishImages(activityId);
            }
            case rejected -> activity.setReviewStatus(ActivityReviewStatus.rejected);
            case changeRequired -> activity.setReviewStatus(ActivityReviewStatus.changeRequired);
            default -> throw new BusinessException(60006, "Review reason is required");
        }
        activity.setUpdatedAt(Instant.now());
        activityRepository.save(activity);

        createReviewRecord(activityId, result, reason, reviewerId);

        log.info(
                "管理员已审核活动，activityId={}, result={}",
                activityId.replace("\n", "\\n").replace("\r", "\\r"),
                result);
        return loadActivityDetail(activity);
    }

    /**
     * 管理员下架活动。
     *
     * <p>前置条件：activityId 对应活动存在；reviewStatus 不为 draft；
     * runtimeStatus 不为 takenDown。
     *
     * <p>后置条件：活动 runtimeStatus 设为 takenDown，创建一条下架审核记录。
     *
     * @param activityId 活动 ID
     * @param reason 下架原因
     * @return 更新后的活动详情
     * @throws BusinessException 60008 活动不存在
     * @throws BusinessException 60009 活动已下架
     * @throws BusinessException 60010 草稿状态活动不允许下架
     * @throws BusinessException 60006 未提供下架原因
     */
    @Transactional
    public ActivityDtos.ActivityDetail takeDownActivity(String activityId, String reason) {
        return takeDownActivity(activityId, null, reason);
    }

    /**
     * 管理员下架活动，并记录操作人。
     *
     * <p>前置条件：activityId 对应活动存在；reviewStatus 不为 draft；runtimeStatus 不为 takenDown；reason 非空。
     *
     * <p>后置条件：活动 runtimeStatus 设为 takenDown，活动图片回退为仅组织者可见，创建一条下架审核记录。
     *
     * <p>不变量：不修改活动审核状态和业务内容字段。
     *
     * @param activityId 活动 ID
     * @param reviewerId 操作管理员 ID，可为空
     * @param reason     下架原因
     * @return 更新后的活动详情
     */
    @Transactional
    public ActivityDtos.ActivityDetail takeDownActivity(String activityId, String reviewerId, String reason) {
        Activity activity = findActivity(activityId);

        // 前置条件：不能重复下架
        if (activity.getRuntimeStatus() == ActivityRuntimeStatus.takenDown) {
            throw new BusinessException(60009, "Activity moderation state does not allow this operation");
        }

        // 前置条件：草稿状态不允许下架
        if (activity.getReviewStatus() == ActivityReviewStatus.draft) {
            throw new BusinessException(60010, "草稿状态的活动不允许下架");
        }

        // 必须提供下架原因
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(60006, "Review reason is required");
        }

        activity.setRuntimeStatus(ActivityRuntimeStatus.takenDown);
        activity.setUpdatedAt(Instant.now());
        activityRepository.save(activity);

        // 下架后活动不再对外可见，图片回退为 activityOwner（私有），立即失效公开 URL
        restrictImages(activityId);

        createReviewRecord(activityId, ReviewStatus.rejected, reason, reviewerId);

        log.info("管理员已下架活动，activityId={}", activityId.replace("\n", "\\n").replace("\r", "\\r"));
        return loadActivityDetail(activity);
    }

    /**
     * 管理员恢复活动。
     *
     * <p>前置条件：activityId 对应活动存在且 runtimeStatus 为 takenDown。
     *
     * <p>后置条件：活动 runtimeStatus 恢复为 notStarted；创建一条恢复审计记录。
     *
     * @param activityId 活动 ID
     * @return 更新后的活动详情
     * @throws BusinessException 60008 活动不存在
     * @throws BusinessException 60009 活动未被下架
     */
    @Transactional
    public ActivityDtos.ActivityDetail restoreActivity(String activityId) {
        return restoreActivity(activityId, null);
    }

    /**
     * 管理员恢复活动，并记录操作人。
     *
     * <p>前置条件：activityId 对应活动存在且 runtimeStatus 为 takenDown。
     *
     * <p>后置条件：活动 runtimeStatus 恢复为 notStarted，活动图片升级为公开访问，创建一条恢复审计记录。
     *
     * <p>不变量：不修改活动审核状态和业务内容字段。
     *
     * @param activityId 活动 ID
     * @param reviewerId 操作管理员 ID，可为空
     * @return 更新后的活动详情
     */
    @Transactional
    public ActivityDtos.ActivityDetail restoreActivity(String activityId, String reviewerId) {
        Activity activity = findActivity(activityId);

        // 前置条件：已下架的活动才能恢复
        if (activity.getRuntimeStatus() != ActivityRuntimeStatus.takenDown) {
            throw new BusinessException(60009, "Activity moderation state does not allow this operation");
        }

        activity.setRuntimeStatus(ActivityRuntimeStatus.notStarted);
        activity.setUpdatedAt(Instant.now());
        activityRepository.save(activity);

        // 恢复后活动重新对外可见，图片恢复为公开访问
        publishImages(activityId);

        createReviewRecord(activityId, ReviewStatus.approved, "管理员恢复活动", reviewerId);

        log.info("管理员已恢复活动，activityId={}", activityId.replace("\n", "\\n").replace("\r", "\\r"));
        return loadActivityDetail(activity);
    }

    /**
     * 查找活动，不存在时抛出 60008。
     */
    private Activity findActivity(String activityId) {
        return activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(60008, "Activity {activityId} does not exist"));
    }

    /**
     * 将活动图片升级为公开访问（publicAccess / publicVisible），用于审核通过和恢复上架。
     *
     * <p>前置条件：调用方已确认活动进入对所有人可见的状态。
     *
     * <p>后置条件：活动关联的未软删除图片访问策略均为 publicAccess，任何人（含匿名）可经签名 URL 访问。
     *
     * @param activityId 活动 ID
     */
    public void publishImages(String activityId) {
        for (MediaFile mediaFile : activityMediaQueryService.loadMediaFiles(activityId)) {
            if (mediaFile.getDeletedAt() == null) {
                mediaAccessService.overrideAccessPolicy(
                        mediaFile.getMediaId(), MediaAccessPolicy.publicAccess, "", MediaVisibility.publicVisible);
            }
        }
    }

    /**
     * 将活动图片回退为仅组织者可见（activityOwner / privateVisible），用于下架。
     *
     * <p>前置条件：调用方已确认活动不再对外可见。
     *
     * <p>后置条件：活动关联的未软删除图片访问策略均为 activityOwner，公开签名 URL 因版本递增立即失效。
     *
     * @param activityId 活动 ID
     */
    public void restrictImages(String activityId) {
        for (MediaFile mediaFile : activityMediaQueryService.loadMediaFiles(activityId)) {
            if (mediaFile.getDeletedAt() == null) {
                mediaAccessService.overrideAccessPolicy(
                        mediaFile.getMediaId(),
                        MediaAccessPolicy.activityOwner,
                        activityId,
                        MediaVisibility.privateVisible);
            }
        }
    }

    /**
     * 创建审核记录。
     *
     * <p>reviewerId 为空表示系统审核或历史调用；非空表示人工管理员操作。
     */
    private void createReviewRecord(String activityId, ReviewStatus result, String reason, String reviewerId) {
        ActivityReviewRecord record = ActivityReviewRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .activityId(activityId)
                .result(result)
                .reason(reason)
                .reviewerId(reviewerId)
                .reviewedAt(Instant.now())
                .build();
        activityReviewRecordRepository.save(record);
    }

    /**
     * 加载活动详情（复用 ActivityQueryService 的模式）。
     */
    private ActivityDtos.ActivityDetail loadActivityDetail(Activity activity) {
        List<MediaFile> mediaFiles = activityMediaQueryService.loadMediaFiles(activity.getActivityId());
        Map<UUID, Integer> sortOrderByMediaId = activityMediaQueryService.loadImageSortOrders(activity.getActivityId());
        String organizerName = userRepository
                .findById(activity.getOrganizerId())
                .map(user -> user.getNickname())
                .orElse("未知用户");
        List<ActivityReviewRecord> reviewRecords =
                activityReviewRecordRepository.findByActivityIdOrderByReviewedAtDesc(activity.getActivityId());
        List<ActivityDtos.ReviewRecord> reviewRecordDtos =
                reviewRecords.stream().map(activityDtoMapper::toReviewRecord).toList();
        ActivityRegistrationCounts counts =
                activityRegistrationCountService.countByActivityId(activity.getActivityId());
        ActivityDtos.ActivityDetail detail = activityDtoMapper.toActivityDetail(
                activity,
                organizerName,
                mediaFiles,
                mediaId -> sortOrderByMediaId.getOrDefault(mediaId, Integer.MAX_VALUE),
                reviewRecordDtos,
                counts);
        return detail;
    }
}

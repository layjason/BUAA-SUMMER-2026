package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityImage;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityReviewRecordRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ActivityImageRepository activityImageRepository;
    private final ActivityReviewRecordRepository activityReviewRecordRepository;
    private final MediaFileRepository mediaFileRepository;
    private final UserRepository userRepository;
    private final ActivityDtoMapper activityDtoMapper;

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
            }
            case rejected -> activity.setReviewStatus(ActivityReviewStatus.rejected);
            case changeRequired -> activity.setReviewStatus(ActivityReviewStatus.changeRequired);
            default -> throw new BusinessException(60006, "Review reason is required");
        }
        activity.setUpdatedAt(Instant.now());
        activityRepository.save(activity);

        createReviewRecord(activityId, result, reason);

        log.info(
                "管理员已审核活动，activityId={}, result={}",
                activityId.replace("\n", "\\n").replace("\r", "\\r"),
                result);
        return loadActivityDetail(activity);
    }

    /**
     * 管理员下架活动。
     *
     * <p>前置条件：activityId 对应活动存在且 runtimeStatus 不为 takenDown。
     *
     * <p>后置条件：活动 runtimeStatus 设为 takenDown，创建一条下架审核记录。
     *
     * @param activityId 活动 ID
     * @param reason 下架原因
     * @return 更新后的活动详情
     * @throws BusinessException 60008 活动不存在
     * @throws BusinessException 60009 活动已下架
     * @throws BusinessException 60006 未提供下架原因
     */
    @Transactional
    public ActivityDtos.ActivityDetail takeDownActivity(String activityId, String reason) {
        Activity activity = findActivity(activityId);

        // 前置条件：不能重复下架
        if (activity.getRuntimeStatus() == ActivityRuntimeStatus.takenDown) {
            throw new BusinessException(60009, "Activity moderation state does not allow this operation");
        }

        // 必须提供下架原因
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(60006, "Review reason is required");
        }

        activity.setRuntimeStatus(ActivityRuntimeStatus.takenDown);
        activity.setUpdatedAt(Instant.now());
        activityRepository.save(activity);

        createReviewRecord(activityId, ReviewStatus.rejected, reason);

        log.info("管理员已下架活动，activityId={}", activityId.replace("\n", "\\n").replace("\r", "\\r"));
        return loadActivityDetail(activity);
    }

    /**
     * 管理员恢复活动。
     *
     * <p>前置条件：activityId 对应活动存在且 runtimeStatus 为 takenDown。
     *
     * <p>后置条件：活动 runtimeStatus 恢复为 notStarted。
     *
     * @param activityId 活动 ID
     * @return 更新后的活动详情
     * @throws BusinessException 60008 活动不存在
     * @throws BusinessException 60009 活动未被下架
     */
    @Transactional
    public ActivityDtos.ActivityDetail restoreActivity(String activityId) {
        Activity activity = findActivity(activityId);

        // 前置条件：已下架的活动才能恢复
        if (activity.getRuntimeStatus() != ActivityRuntimeStatus.takenDown) {
            throw new BusinessException(60009, "Activity moderation state does not allow this operation");
        }

        activity.setRuntimeStatus(ActivityRuntimeStatus.notStarted);
        activity.setUpdatedAt(Instant.now());
        activityRepository.save(activity);

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
     * 创建审核记录。
     *
     * <p>reviewerId 暂不记录，待管理员认证体系完成后补充。
     */
    private void createReviewRecord(String activityId, ReviewStatus result, String reason) {
        ActivityReviewRecord record = ActivityReviewRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .activityId(activityId)
                .result(result)
                .reason(reason)
                .reviewedAt(Instant.now())
                .build();
        activityReviewRecordRepository.save(record);
    }

    /**
     * 加载活动详情（复用 ActivityQueryService 的模式）。
     */
    private ActivityDtos.ActivityDetail loadActivityDetail(Activity activity) {
        List<ActivityImage> activityImages =
                activityImageRepository.findByActivityIdOrderBySortOrderAsc(activity.getActivityId());
        List<MediaFile> mediaFiles = loadMediaFiles(activityImages);
        Map<String, Integer> sortOrderByMediaId = new LinkedHashMap<>();
        for (ActivityImage activityImage : activityImages) {
            sortOrderByMediaId.put(activityImage.getMediaId(), activityImage.getSortOrder());
        }
        String organizerName = userRepository
                .findById(activity.getOrganizerId())
                .map(user -> user.getNickname())
                .orElse("未知用户");
        List<ActivityReviewRecord> reviewRecords =
                activityReviewRecordRepository.findByActivityIdOrderByReviewedAtDesc(activity.getActivityId());
        List<ActivityDtos.ReviewRecord> reviewRecordDtos =
                reviewRecords.stream().map(activityDtoMapper::toReviewRecord).toList();
        return activityDtoMapper.toActivityDetail(
                activity,
                organizerName,
                mediaFiles,
                mediaId -> sortOrderByMediaId.getOrDefault(mediaId, Integer.MAX_VALUE),
                reviewRecordDtos);
    }

    private List<MediaFile> loadMediaFiles(List<ActivityImage> activityImages) {
        if (activityImages.isEmpty()) {
            return List.of();
        }
        List<String> mediaIds =
                activityImages.stream().map(ActivityImage::getMediaId).toList();
        Map<String, MediaFile> mediaFileMap = mediaFileRepository.findByMediaIdIn(mediaIds).stream()
                .collect(Collectors.toMap(MediaFile::getMediaId, mediaFile -> mediaFile));
        return mediaIds.stream().map(mediaFileMap::get).filter(Objects::nonNull).toList();
    }
}

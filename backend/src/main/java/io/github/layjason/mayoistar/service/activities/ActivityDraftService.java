package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityImage;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ActivityReviewRecordRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动草稿服务。
 *
 * <p>类职责：管理活动草稿的保存、读取和更新流程，保证草稿仅能由创建者访问，并维护草稿关联图片顺序。
 *
 * <p>类不变量：草稿记录的 organizerId 一旦创建后不被修改；草稿服务操作 reviewStatus=draft 或 changeRequired
 * 的活动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityDraftService {

    private final ActivityRepository activityRepository;
    private final ActivityImageRepository activityImageRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityReviewRecordRepository activityReviewRecordRepository;
    private final MediaFileRepository mediaFileRepository;
    private final UserRepository userRepository;
    private final ActivityDraftMapper activityDraftMapper;
    private final SubmitActivityValidator submitActivityValidator;

    /**
     * 保存新的活动草稿。
     *
     * <p>前置条件：调用者用户存在；请求中的时间范围、报名截止时间和地点结构满足草稿规则。
     *
     * <p>后置条件：创建一条 reviewStatus=draft 的活动记录，并返回带图片回显的草稿详情。
     *
     * <p>不变量：新草稿的组织者固定为调用者本人，运行状态初始化为 notStarted。
     *
     * @param organizerId 当前调用者 ID
     * @param request 草稿保存请求
     * @return 新建草稿详情
     */
    @Transactional
    public ActivityDtos.ActivityDraftDetail saveDraft(
            String organizerId, ActivityDtos.ActivityDraftUpsertRequest request) {
        validateUserExists(organizerId);
        CommonDtos.LocationInfo location = request.getLocation();
        validateDraftRequest(request, location);

        Instant now = Instant.now();
        Activity activity = Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title(request.getTitle())
                .tags(copyTags(request.getTags()))
                .introduction(request.getIntroduction())
                .startAt(parseInstant(request.getStartAt(), "活动开始时间"))
                .endAt(parseInstant(request.getEndAt(), "活动结束时间"))
                .pointLon(locationPointLongitude(location))
                .pointLat(locationPointLatitude(location))
                .city(location == null ? null : location.getCity())
                .address(location == null ? null : location.getAddress())
                .placeName(location == null ? null : location.getPlaceName())
                .safetyNotice(request.getSafetyNotice())
                .capacity(request.getCapacity())
                .feeAmount(request.getFeeAmount())
                .feeDescription(request.getFeeDescription())
                .minAge(request.getMinAge())
                .registrationDeadline(parseInstant(request.getRegistrationDeadline(), "报名截止时间"))
                .reviewStatus(ActivityReviewStatus.draft)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .manualReviewRequired(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Activity savedActivity = activityRepository.save(activity);
        replaceImages(savedActivity.getActivityId(), request.getImageIds());
        log.info("已保存活动草稿，activityId={}, organizerId={}", savedActivity.getActivityId(), organizerId);
        return loadDraftDetail(savedActivity);
    }

    /**
     * 分页获取当前用户的活动草稿。
     *
     * <p>前置条件：调用者用户存在；分页参数为空时采用默认值。
     *
     * <p>后置条件：仅返回调用者自己创建且处于草稿状态或要求修改状态的活动，按更新时间倒序排列。
     *
     * <p>不变量：不会返回已提交审核、已通过、已驳回的活动。
     *
     * @param organizerId 当前调用者 ID
     * @param page 页码，从 1 开始
     * @param pageSize 每页大小
     * @return 草稿分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivityDraftSummary> listDrafts(
            String organizerId, Integer page, Integer pageSize) {
        validateUserExists(organizerId);
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        PageResult<ActivityDtos.ActivityDraftSummary> result = activityDraftMapper.toDraftSummaryPage(
                activityRepository.findByOrganizerIdAndReviewStatusInOrderByUpdatedAtDesc(
                        organizerId,
                        Set.of(ActivityReviewStatus.draft, ActivityReviewStatus.changeRequired),
                        PageRequest.of(resolvedPage - 1, resolvedPageSize)));
        log.debug(
                "已查询活动草稿列表，organizerId={}, page={}, pageSize={}, total={}",
                organizerId,
                resolvedPage,
                resolvedPageSize,
                result.getTotal());
        return result;
    }

    /**
     * 查询单个活动草稿详情。
     *
     * <p>前置条件：调用者用户存在；activityId 对应记录存在。
     *
     * <p>后置条件：当草稿属于调用者本人时返回详情，否则抛出 403 或 404。
     *
     * <p>不变量：仅允许读取 reviewStatus=draft 或 changeRequired 的活动。
     *
     * @param organizerId 当前调用者 ID
     * @param activityId 草稿 ID
     * @return 草稿详情
     */
    @Transactional(readOnly = true)
    public ActivityDtos.ActivityDraftDetail getDraft(String organizerId, String activityId) {
        validateUserExists(organizerId);
        return loadDraftDetail(findOwnedDraft(organizerId, activityId));
    }

    /**
     * 更新活动草稿。
     *
     * <p>前置条件：调用者用户存在；草稿存在且属于调用者本人；草稿处于 draft 或 changeRequired 状态。
     *
     * <p>后置条件：草稿信息和图片顺序被新请求覆盖，updatedAt 刷新为当前时间。
     *
     * <p>不变量：organizerId、reviewStatus 与 runtimeStatus 不因草稿更新而改变。
     *
     * @param organizerId 当前调用者 ID
     * @param activityId 草稿 ID
     * @param request 草稿更新请求
     * @return 更新后的草稿详情
     */
    @Transactional
    public ActivityDtos.ActivityDraftDetail updateDraft(
            String organizerId, String activityId, ActivityDtos.ActivityDraftUpsertRequest request) {
        validateUserExists(organizerId);
        Activity activity = findOwnedDraft(organizerId, activityId);
        CommonDtos.LocationInfo location = request.getLocation();
        validateDraftRequest(request, location);

        activity.setTitle(request.getTitle());
        activity.setTags(copyTags(request.getTags()));
        activity.setIntroduction(request.getIntroduction());
        activity.setStartAt(parseInstant(request.getStartAt(), "活动开始时间"));
        activity.setEndAt(parseInstant(request.getEndAt(), "活动结束时间"));
        activity.setPointLon(locationPointLongitude(location));
        activity.setPointLat(locationPointLatitude(location));
        activity.setCity(location == null ? null : location.getCity());
        activity.setAddress(location == null ? null : location.getAddress());
        activity.setPlaceName(location == null ? null : location.getPlaceName());
        activity.setSafetyNotice(request.getSafetyNotice());
        activity.setCapacity(request.getCapacity());
        activity.setFeeAmount(request.getFeeAmount());
        activity.setFeeDescription(request.getFeeDescription());
        activity.setMinAge(request.getMinAge());
        activity.setRegistrationDeadline(parseInstant(request.getRegistrationDeadline(), "报名截止时间"));
        activity.setUpdatedAt(Instant.now());
        Activity savedActivity = activityRepository.save(activity);
        replaceImages(savedActivity.getActivityId(), request.getImageIds());
        log.info("已更新活动草稿，activityId={}, organizerId={}", savedActivity.getActivityId(), organizerId);
        return loadDraftDetail(savedActivity);
    }

    /**
     * 提交活动审核。
     *
     * <p>前置条件：调用者用户存在；活动存在且属于调用者本人；活动处于 draft 或 changeRequired 状态；
     * 活动所有必填字段完整且合法。
     *
     * <p>后置条件：活动 reviewStatus 更新为 pending；创建一条审核记录（初始结果为 pending）；
     * 若容量达到人工审核阈值则标记 manualReviewRequired。
     *
     * <p>不变量：organizerId 不变；runtimeStatus 不变。
     *
     * @param organizerId 当前调用者 ID
     * @param activityId 活动 ID
     * @return 活动详情
     */
    @Transactional
    public ActivityDtos.ActivityDetail submitActivity(String organizerId, String activityId) {
        validateUserExists(organizerId);
        Activity activity = findActivityForSubmit(organizerId, activityId);
        submitActivityValidator.validateForSubmission(activity);

        activity.setReviewStatus(ActivityReviewStatus.pending);
        activity.setManualReviewRequired(shouldManualReview(activity));
        activity.setUpdatedAt(Instant.now());
        Activity savedActivity = activityRepository.save(activity);

        ActivityReviewRecord reviewRecord = createInitialReviewRecord(savedActivity.getActivityId());
        activityReviewRecordRepository.save(reviewRecord);

        log.info(
                "已提交活动审核，activityId={}, organizerId={}, manualReviewRequired={}",
                savedActivity.getActivityId(),
                organizerId,
                savedActivity.getManualReviewRequired());

        return loadActivityDetail(savedActivity);
    }

    private Activity findActivityForSubmit(String organizerId, String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity {activityId} is not visible"));
        if (!activity.getOrganizerId().equals(organizerId)) {
            throw new BusinessException(ErrorCodes.ACTIVITY_PERMISSION_DENIED, "无权操作其他用户的活动");
        }
        return activity;
    }

    /**
     * 判断是否需要人工审核。
     *
     * <p>前置条件：activity 非空且 capacity 已通过校验。
     *
     * <p>后置条件：capacity >= 50 时返回 true，否则返回 false。
     *
     * <p>不变量：不修改实体。
     *
     * @param activity 活动实体
     * @return 是否需要人工审核
     */
    private boolean shouldManualReview(Activity activity) {
        return activity.getCapacity() != null && activity.getCapacity() >= 50;
    }

    /**
     * 创建初始审核记录。
     *
     * <p>前置条件：activityId 非空。
     *
     * <p>后置条件：返回一条 result=pending、reviewerId 为空的审核记录。
     *
     * <p>不变量：不修改活动实体，不执行除创建外的其他持久化操作。
     *
     * @param activityId 活动 ID
     * @return 初始审核记录
     */
    private ActivityReviewRecord createInitialReviewRecord(String activityId) {
        return ActivityReviewRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .activityId(activityId)
                .result(ReviewStatus.pending)
                .reviewedAt(Instant.now())
                .build();
    }

    /**
     * 加载活动详情（含发起人昵称、图片、审核记录）。
     *
     * <p>前置条件：activity 已持久化。
     *
     * <p>后置条件：返回包含完整字段的 ActivityDetail。
     *
     * <p>不变量：不修改传入实体。
     *
     * @param activity 活动实体
     * @return 活动详情 DTO
     */
    private ActivityDtos.ActivityDetail loadActivityDetail(Activity activity) {
        List<ActivityImage> activityImages =
                activityImageRepository.findByActivityIdOrderBySortOrderAsc(activity.getActivityId());
        List<MediaFile> mediaFiles = loadMediaFiles(activityImages);
        Map<UUID, Integer> sortOrderByMediaId = new LinkedHashMap<>();
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
                reviewRecords.stream().map(activityDraftMapper::toReviewRecord).toList();
        return activityDraftMapper.toActivityDetail(
                activity,
                organizerName,
                mediaFiles,
                mediaId -> sortOrderByMediaId.getOrDefault(mediaId, Integer.MAX_VALUE),
                reviewRecordDtos,
                countByStatus(activity.getActivityId(), RegistrationStatus.registered, RegistrationStatus.checkedIn),
                countByStatus(
                        activity.getActivityId(), RegistrationStatus.waiting, RegistrationStatus.waitingConfirmation));
    }

    private Activity findOwnedDraft(String organizerId, String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity {activityId} is not visible"));
        if (!activity.getOrganizerId().equals(organizerId)) {
            throw new BusinessException(ErrorCodes.ACTIVITY_PERMISSION_DENIED, "无权访问其他用户的活动草稿");
        }
        if (activity.getReviewStatus() != ActivityReviewStatus.draft
                && activity.getReviewStatus() != ActivityReviewStatus.changeRequired) {
            throw new BusinessException(ErrorCodes.ACTIVITY_STATE_NOT_SUBMITTABLE, "当前活动不允许按草稿规则访问");
        }
        return activity;
    }

    private ActivityDtos.ActivityDraftDetail loadDraftDetail(Activity activity) {
        List<ActivityImage> activityImages =
                activityImageRepository.findByActivityIdOrderBySortOrderAsc(activity.getActivityId());
        List<MediaFile> mediaFiles = loadMediaFiles(activityImages);
        Map<UUID, Integer> sortOrderByMediaId = new LinkedHashMap<>();
        for (ActivityImage activityImage : activityImages) {
            sortOrderByMediaId.put(activityImage.getMediaId(), activityImage.getSortOrder());
        }
        return activityDraftMapper.toDraftDetail(
                activity, mediaFiles, mediaId -> sortOrderByMediaId.getOrDefault(mediaId, Integer.MAX_VALUE));
    }

    private List<MediaFile> loadMediaFiles(List<ActivityImage> activityImages) {
        if (activityImages.isEmpty()) {
            return List.of();
        }
        List<UUID> mediaIds =
                activityImages.stream().map(ActivityImage::getMediaId).toList();
        Map<UUID, MediaFile> mediaFileMap = mediaFileRepository.findByMediaIdIn(mediaIds).stream()
                .collect(Collectors.toMap(MediaFile::getMediaId, mediaFile -> mediaFile));
        return mediaIds.stream().map(mediaFileMap::get).filter(Objects::nonNull).toList();
    }

    private void replaceImages(String activityId, Collection<UUID> imageIds) {
        activityImageRepository.deleteByActivityId(activityId);
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        validateMediaFiles(imageIds);
        int index = 0;
        for (UUID imageId : imageIds) {
            activityImageRepository.save(ActivityImage.builder()
                    .imageId(UUID.randomUUID().toString())
                    .activityId(activityId)
                    .mediaId(imageId)
                    .sortOrder(index++)
                    .build());
        }
    }

    private void validateMediaFiles(Collection<UUID> mediaIds) {
        List<MediaFile> mediaFiles = mediaFileRepository.findByMediaIdIn(mediaIds);
        if (mediaFiles.size() != mediaIds.size()) {
            throw new BusinessException(ErrorCodes.MEDIA_FILE_UNAVAILABLE, "存在不可用的活动图片");
        }
    }

    private void validateUserExists(String organizerId) {
        if (!userRepository.existsById(organizerId)) {
            throw new BusinessException(400, "当前调用者不存在");
        }
    }

    private void validateDraftRequest(
            ActivityDtos.ActivityDraftUpsertRequest request, CommonDtos.LocationInfo location) {
        Instant startAt = parseInstant(request.getStartAt(), "活动开始时间");
        Instant endAt = parseInstant(request.getEndAt(), "活动结束时间");
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动结束时间必须晚于开始时间");
        }
        Instant registrationDeadline = parseInstant(request.getRegistrationDeadline(), "报名截止时间");
        if (registrationDeadline != null && startAt != null && registrationDeadline.isAfter(startAt)) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "报名截止时间不能晚于活动开始时间");
        }
        if (request.getCapacity() != null && request.getCapacity() < 1) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动人数上限必须大于 0");
        }
        if (request.getMinAge() != null && request.getMinAge() < 0) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "最小年龄不能为负数");
        }
        if (location != null && location.getPoint() != null) {
            Double longitude = location.getPoint().getLongitude();
            Double latitude = location.getPoint().getLatitude();
            if (longitude == null || latitude == null) {
                throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动地点坐标必须同时提供经纬度");
            }
        }
    }

    private Instant parseInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, fieldName + "格式不合法");
        }
    }

    private List<String> copyTags(List<String> tags) {
        return tags == null ? null : List.copyOf(tags);
    }

    private Double locationPointLongitude(CommonDtos.LocationInfo location) {
        return location == null || location.getPoint() == null
                ? null
                : location.getPoint().getLongitude();
    }

    private Double locationPointLatitude(CommonDtos.LocationInfo location) {
        return location == null || location.getPoint() == null
                ? null
                : location.getPoint().getLatitude();
    }

    /**
     * 统计指定活动在给定状态集合中的报名记录数。
     */
    private int countByStatus(String activityId, RegistrationStatus... statuses) {
        return activityRegistrationRepository
                .findByActivityIdAndStatusIn(activityId, Set.of(statuses))
                .size();
    }
}

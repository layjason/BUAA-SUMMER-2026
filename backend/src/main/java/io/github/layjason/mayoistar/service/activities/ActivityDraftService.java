package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.BusinessException;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityImage;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRepository;
import io.github.layjason.mayoistar.repository.common.MediaFileRepository;
import io.github.layjason.mayoistar.repository.identity.UserRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <p>类不变量：草稿记录的 organizerId 一旦创建后不被修改；草稿服务只操作 reviewStatus=draft 的草稿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityDraftService {

    private final ActivityRepository activityRepository;
    private final ActivityImageRepository activityImageRepository;
    private final MediaFileRepository mediaFileRepository;
    private final UserRepository userRepository;
    private final ActivityDraftMapper activityDraftMapper;

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
     * <p>后置条件：仅返回调用者自己创建且仍处于草稿状态的活动，按更新时间倒序排列。
     *
     * <p>不变量：不会返回已提交审核或其他状态的活动。
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
                activityRepository.findByOrganizerIdAndReviewStatusOrderByUpdatedAtDesc(
                        organizerId, ActivityReviewStatus.draft, PageRequest.of(resolvedPage - 1, resolvedPageSize)));
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
     * <p>不变量：仅允许读取 reviewStatus=draft 的活动。
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
     * <p>前置条件：调用者用户存在；草稿存在且属于调用者本人；草稿仍处于 draft 状态。
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

    private Activity findOwnedDraft(String organizerId, String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(20002, "活动草稿不存在"));
        if (!activity.getOrganizerId().equals(organizerId)) {
            throw new BusinessException(20003, "无权访问其他用户的活动草稿");
        }
        if (activity.getReviewStatus() != ActivityReviewStatus.draft) {
            throw new BusinessException(20005, "当前活动不允许按草稿规则访问");
        }
        return activity;
    }

    private ActivityDtos.ActivityDraftDetail loadDraftDetail(Activity activity) {
        List<ActivityImage> activityImages =
                activityImageRepository.findByActivityIdOrderBySortOrderAsc(activity.getActivityId());
        List<MediaFile> mediaFiles = loadMediaFiles(activityImages);
        Map<String, Integer> sortOrderByMediaId = new LinkedHashMap<>();
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
        List<String> mediaIds =
                activityImages.stream().map(ActivityImage::getMediaId).toList();
        Map<String, MediaFile> mediaFileMap = mediaFileRepository.findByMediaIdIn(mediaIds).stream()
                .collect(Collectors.toMap(MediaFile::getMediaId, mediaFile -> mediaFile));
        return mediaIds.stream().map(mediaFileMap::get).filter(Objects::nonNull).toList();
    }

    private void replaceImages(String activityId, Collection<String> imageIds) {
        activityImageRepository.deleteByActivityId(activityId);
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        validateMediaFiles(imageIds);
        int index = 0;
        for (String imageId : imageIds) {
            activityImageRepository.save(ActivityImage.builder()
                    .imageId(UUID.randomUUID().toString())
                    .activityId(activityId)
                    .mediaId(imageId)
                    .sortOrder(index++)
                    .build());
        }
    }

    private void validateMediaFiles(Collection<String> mediaIds) {
        List<MediaFile> mediaFiles = mediaFileRepository.findByMediaIdIn(mediaIds);
        if (mediaFiles.size() != mediaIds.size()) {
            throw new BusinessException(20017, "存在不可用的活动图片");
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
            throw new BusinessException(20004, "活动结束时间必须晚于开始时间");
        }
        Instant registrationDeadline = parseInstant(request.getRegistrationDeadline(), "报名截止时间");
        if (registrationDeadline != null && startAt != null && registrationDeadline.isAfter(startAt)) {
            throw new BusinessException(20004, "报名截止时间不能晚于活动开始时间");
        }
        if (request.getCapacity() != null && request.getCapacity() < 1) {
            throw new BusinessException(20004, "活动人数上限必须大于 0");
        }
        if (request.getMinAge() != null && request.getMinAge() < 0) {
            throw new BusinessException(20004, "最小年龄不能为负数");
        }
        if (location != null && location.getPoint() != null) {
            Double longitude = location.getPoint().getLongitude();
            Double latitude = location.getPoint().getLatitude();
            if (longitude == null || latitude == null) {
                throw new BusinessException(20004, "活动地点坐标必须同时提供经纬度");
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
            throw new BusinessException(20004, fieldName + "格式不合法");
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
}

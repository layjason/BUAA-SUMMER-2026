package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * 活动查询映射器。
 *
 * <p>类职责：负责活动查询（详情、摘要、我的活动列表）的领域对象到 API DTO 转换，
 * 与草稿映射器分离以保持各自职责清晰。
 *
 * <p>类不变量：映射过程不修改传入实体，不执行持久化操作。
 */
@Component
@RequiredArgsConstructor
public class ActivityDtoMapper {

    private final MediaAccessService mediaAccessService;

    /**
     * 将活动实体分页转换为 ActivitySummary 分页结果。
     *
     * <p>前置条件：activityPage 非空，coverImageProvider 可为空（无封面图时返回 null）。
     *
     * <p>后置条件：分页信息与输入一致，摘要 DTO 包含封面图。
     *
     * <p>不变量：不修改传入实体。
     *
     * @param activityPage 活动分页
     * @param coverImageProvider 根据活动 ID 获取封面图，可能返回 null
     * @return ActivitySummary 分页结果
     */
    public PageResult<ActivityDtos.ActivitySummary> toActivitySummaryPage(
            Page<Activity> activityPage, Function<String, CommonDtos.MediaFile> coverImageProvider) {
        List<ActivityDtos.ActivitySummary> items = activityPage.getContent().stream()
                .map(activity -> toActivitySummary(activity, coverImageProvider))
                .toList();
        return new PageResult<>(
                items,
                activityPage.getTotalElements(),
                activityPage.getNumber() + 1,
                activityPage.getSize(),
                activityPage.getTotalPages());
    }

    /**
     * 将活动实体转为 ActivitySummary DTO。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：返回包含基本摘要字段的 DTO，registeredCount 暂为 0（由报名模块补充）。
     *
     * <p>不变量：不修改传入实体。
     *
     * @param activity 活动实体
     * @param coverImageProvider 根据活动 ID 获取封面图
     * @return ActivitySummary DTO
     */
    public ActivityDtos.ActivitySummary toActivitySummary(
            Activity activity, Function<String, CommonDtos.MediaFile> coverImageProvider) {
        ActivityDtos.ActivitySummary dto = new ActivityDtos.ActivitySummary();
        fillActivitySummary(dto, activity, coverImageProvider);
        return dto;
    }

    /**
     * 将报名记录转为当前用户报名活动摘要 DTO。
     *
     * <p>前置条件：registration 非空且已加载关联 Activity。
     *
     * <p>后置条件：返回活动摘要字段以及当前用户的报名状态字段。
     *
     * <p>不变量：不修改传入实体。
     *
     * @param registration 报名记录
     * @param coverImageProvider 根据活动 ID 获取封面图
     * @return 当前用户报名活动摘要 DTO
     */
    public ActivityDtos.RegisteredActivitySummary toRegisteredActivitySummary(
            ActivityRegistration registration, Function<String, CommonDtos.MediaFile> coverImageProvider) {
        Activity activity = registration.getActivity();
        ActivityDtos.RegisteredActivitySummary dto = new ActivityDtos.RegisteredActivitySummary();
        fillActivitySummary(dto, activity, coverImageProvider);
        dto.setRegistrationId(registration.getRegistrationId());
        dto.setRegistrationStatus(registration.getStatus());
        dto.setRegisteredAt(formatInstant(registration.getRegisteredAt()));
        dto.setWaitingRank(registration.getWaitingRank());
        dto.setConfirmationDeadline(formatInstant(registration.getConfirmationDeadline()));
        return dto;
    }

    private void fillActivitySummary(
            ActivityDtos.ActivitySummary dto,
            Activity activity,
            Function<String, CommonDtos.MediaFile> coverImageProvider) {
        dto.setActivityId(activity.getActivityId());
        dto.setTitle(activity.getTitle());
        dto.setTags(activity.getTags() == null ? List.of() : List.copyOf(activity.getTags()));
        dto.setStartAt(formatInstant(activity.getStartAt()));
        dto.setEndAt(formatInstant(activity.getEndAt()));
        dto.setLocation(toLocationInfo(activity));
        dto.setCoverImage(coverImageProvider.apply(activity.getActivityId()));
        dto.setFeeAmount(activity.getFeeAmount());
        dto.setReviewStatus(activity.getReviewStatus());
        dto.setRuntimeStatus(activity.getRuntimeStatus());
        // registeredCount 由报名模块补充，当前暂为 0
        dto.setRegisteredCount(0);
        dto.setCapacity(activity.getCapacity());
        dto.setRequireLocationCheck(activity.getRequireLocationCheck());
    }

    /**
     * 将活动实体转为 ActivityDetail DTO。
     *
     * <p>前置条件：activity 非空，organizerName 非空。
     *
     * <p>后置条件：返回包含完整详情字段的 DTO，registeredCount 和 waitingCount 暂为 0（由报名模块补充）。
     *
     * <p>不变量：不修改传入实体。
     *
     * @param activity 活动实体
     * @param organizerName 发起人昵称
     * @param mediaFiles 活动图片
     * @param imageSortOrderProvider 图片排序函数
     * @param reviewRecords 审核记录 DTO 列表
     * @return ActivityDetail DTO
     */
    public ActivityDtos.ActivityDetail toActivityDetail(
            Activity activity,
            String organizerName,
            List<MediaFile> mediaFiles,
            Function<UUID, Integer> imageSortOrderProvider,
            List<ActivityDtos.ReviewRecord> reviewRecords) {
        ActivityDtos.ActivityDetail dto = new ActivityDtos.ActivityDetail();
        dto.setActivityId(activity.getActivityId());
        dto.setTitle(activity.getTitle());
        dto.setTags(activity.getTags() == null ? List.of() : List.copyOf(activity.getTags()));
        dto.setStartAt(formatInstant(activity.getStartAt()));
        dto.setEndAt(formatInstant(activity.getEndAt()));
        dto.setLocation(toLocationInfo(activity));
        dto.setCoverImage(mediaFiles.stream()
                .sorted((left, right) -> Integer.compare(
                        imageSortOrderProvider.apply(left.getMediaId()),
                        imageSortOrderProvider.apply(right.getMediaId())))
                .map(this::toMediaFile)
                .findFirst()
                .orElse(null));
        dto.setFeeAmount(activity.getFeeAmount());
        dto.setReviewStatus(activity.getReviewStatus());
        dto.setRuntimeStatus(activity.getRuntimeStatus());
        // registeredCount 由报名模块补充，当前暂为 0
        dto.setRegisteredCount(0);
        dto.setCapacity(activity.getCapacity());
        dto.setIntroduction(activity.getIntroduction());
        dto.setSafetyNotice(activity.getSafetyNotice());
        dto.setRegistrationDeadline(formatInstant(activity.getRegistrationDeadline()));
        dto.setOrganizerId(activity.getOrganizerId());
        dto.setOrganizerName(organizerName);
        dto.setImages(mediaFiles.stream()
                .sorted((left, right) -> Integer.compare(
                        imageSortOrderProvider.apply(left.getMediaId()),
                        imageSortOrderProvider.apply(right.getMediaId())))
                .map(this::toMediaFile)
                .toList());
        // waitingCount 由报名模块补充，当前暂为 0
        dto.setWaitingCount(0);
        dto.setManualReviewRequired(activity.getManualReviewRequired());
        dto.setFeeDescription(activity.getFeeDescription());
        dto.setMinAge(activity.getMinAge());
        dto.setReviewRecords(reviewRecords);
        dto.setRequireLocationCheck(activity.getRequireLocationCheck());
        return dto;
    }

    /**
     * 将审核记录实体转换为审核记录 DTO。
     *
     * <p>前置条件：record 非空。
     *
     * <p>后置条件：返回包含审核结果、原因、审核人和时间的 DTO。
     *
     * <p>不变量：不修改传入实体。
     *
     * @param record 审核记录实体
     * @return 审核记录 DTO
     */
    public ActivityDtos.ReviewRecord toReviewRecord(ActivityReviewRecord record) {
        ActivityDtos.ReviewRecord dto = new ActivityDtos.ReviewRecord();
        dto.setReviewId(record.getRecordId());
        dto.setReviewerId(record.getReviewerId());
        dto.setResult(record.getResult());
        dto.setReason(record.getReason());
        dto.setReviewedAt(formatInstant(record.getReviewedAt()));
        return dto;
    }

    private CommonDtos.LocationInfo toLocationInfo(Activity activity) {
        Double lon = activity.getPointLon();
        Double lat = activity.getPointLat();
        String city = activity.getCity();
        String address = activity.getAddress();
        if (lon == null || lat == null || city == null || address == null) {
            return null;
        }
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(lon);
        point.setLatitude(lat);
        CommonDtos.LocationInfo dto = new CommonDtos.LocationInfo();
        dto.setPoint(point);
        dto.setCity(city);
        dto.setAddress(address);
        dto.setPlaceName(activity.getPlaceName());
        return dto;
    }

    private CommonDtos.MediaFile toMediaFile(MediaFile mediaFile) {
        return mediaAccessService.toSignedDto(mediaFile);
    }

    /**
     * 将活动实体转为地图点 DTO。
     *
     * <p>前置条件：activity 非空且包含有效坐标。
     *
     * <p>后置条件：返回包含 ID、标题、坐标、运行状态和开始时间的 DTO。
     *
     * <p>不变量：不修改传入实体。
     *
     * @param activity 活动实体
     * @return 地图点 DTO
     */
    public ActivityDtos.ActivityMapPoint toActivityMapPoint(Activity activity) {
        ActivityDtos.ActivityMapPoint dto = new ActivityDtos.ActivityMapPoint();
        dto.setActivityId(activity.getActivityId());
        dto.setTitle(activity.getTitle());
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(activity.getPointLon());
        point.setLatitude(activity.getPointLat());
        dto.setPoint(point);
        dto.setRuntimeStatus(activity.getRuntimeStatus());
        dto.setStartAt(formatInstant(activity.getStartAt()));
        return dto;
    }

    private String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}

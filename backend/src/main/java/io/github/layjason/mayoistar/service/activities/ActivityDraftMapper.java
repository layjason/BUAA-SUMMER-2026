package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import io.github.layjason.mayoistar.entity.activities.ActivityTemplate;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.service.ai.AiContentReviewSnapshotMapper;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * 活动草稿映射器。
 *
 * <p>类职责：负责活动草稿领域对象与 API DTO 之间的转换，集中处理时间、地点和图片列表的投影逻辑。
 *
 * <p>类不变量：映射过程不修改传入实体，不执行持久化操作。
 */
@Component
@RequiredArgsConstructor
public class ActivityDraftMapper {

    private final MediaAccessService mediaAccessService;
    private final AiContentReviewSnapshotMapper aiContentReviewSnapshotMapper;

    public PageResult<ActivityDtos.ActivityDraftSummary> toDraftSummaryPage(Page<Activity> activityPage) {
        List<ActivityDtos.ActivityDraftSummary> items =
                activityPage.getContent().stream().map(this::toDraftSummary).toList();
        return new PageResult<>(
                items,
                activityPage.getTotalElements(),
                activityPage.getNumber() + 1,
                activityPage.getSize(),
                activityPage.getTotalPages());
    }

    public ActivityDtos.ActivityDraftSummary toDraftSummary(Activity activity) {
        ActivityDtos.ActivityDraftSummary dto = new ActivityDtos.ActivityDraftSummary();
        dto.setActivityId(activity.getActivityId());
        dto.setTitle(toDraftTitle(activity.getTitle()));
        dto.setReviewStatus(activity.getReviewStatus());
        dto.setUpdatedAt(formatInstant(activity.getUpdatedAt()));
        dto.setCreatedAt(formatInstant(activity.getCreatedAt()));
        return dto;
    }

    public PageResult<ActivityDtos.ActivityTemplate> toTemplatePage(Page<ActivityTemplate> templatePage) {
        List<ActivityDtos.ActivityTemplate> items =
                templatePage.getContent().stream().map(this::toTemplate).toList();
        return new PageResult<>(
                items,
                templatePage.getTotalElements(),
                templatePage.getNumber() + 1,
                templatePage.getSize(),
                templatePage.getTotalPages());
    }

    public ActivityDtos.ActivityTemplate toTemplate(ActivityTemplate template) {
        ActivityDtos.ActivityTemplate dto = new ActivityDtos.ActivityTemplate();
        dto.setTemplateId(template.getTemplateId());
        dto.setName(template.getName());
        dto.setActivityType(template.getActivityType());
        dto.setDefaultTags(template.getDefaultTags() == null ? List.of() : List.copyOf(template.getDefaultTags()));
        dto.setDefaultIntroduction(template.getDefaultIntroduction());
        dto.setDefaultSafetyNotice(template.getDefaultSafetyNotice());
        dto.setDefaultCapacity(template.getDefaultCapacity());
        dto.setDefaultCoverImage(
                template.getDefaultCoverImage() == null ? null : toMediaFile(template.getDefaultCoverImage()));
        return dto;
    }

    public ActivityDtos.ActivityDraftDetail toDraftDetail(
            Activity activity, Collection<MediaFile> mediaFiles, Function<UUID, Integer> imageSortOrderProvider) {
        ActivityDtos.ActivityDraftDetail dto = new ActivityDtos.ActivityDraftDetail();
        dto.setActivityId(activity.getActivityId());
        dto.setTitle(toDraftTitle(activity.getTitle()));
        dto.setTags(activity.getTags() == null ? List.of() : List.copyOf(activity.getTags()));
        dto.setIntroduction(activity.getIntroduction());
        dto.setStartAt(formatDraftInstant(activity.getStartAt()));
        dto.setEndAt(formatDraftInstant(activity.getEndAt()));
        dto.setLocation(toLocationInfo(activity));
        dto.setSafetyNotice(activity.getSafetyNotice());
        dto.setCapacity(toDraftCapacity(activity.getCapacity()));
        dto.setRegistrationDeadline(formatInstant(activity.getRegistrationDeadline()));
        dto.setFeeAmount(activity.getFeeAmount() != null ? activity.getFeeAmount() : java.math.BigDecimal.ZERO);
        dto.setFeeDescription(activity.getFeeDescription());
        dto.setMinAge(activity.getMinAge());
        dto.setImages(mediaFiles.stream()
                .sorted((left, right) -> Integer.compare(
                        imageSortOrderProvider.apply(left.getMediaId()),
                        imageSortOrderProvider.apply(right.getMediaId())))
                .map(this::toMediaFile)
                .toList());
        dto.setReviewStatus(activity.getReviewStatus());
        dto.setUpdatedAt(formatInstant(activity.getUpdatedAt()));
        dto.setCreatedAt(formatInstant(activity.getCreatedAt()));
        dto.setRequireLocationCheck(activity.getRequireLocationCheck());
        return dto;
    }

    /**
     * 将活动实体中的地点字段转换为 LocationInfo DTO。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：仅当 point（经纬度均非空）、city、address 三者齐全时返回 LocationInfo，
     * 否则返回 null，确保返回值符合 TypeSpec LocationInfo 契约（point/city/address 均为必填）。
     *
     * <p>不变量：不修改传入实体。
     */
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
     * 将活动实体转换为活动详情 DTO。
     *
     * <p>前置条件：activity 非空，organizerName 非空。
     *
     * <p>后置条件：返回包含完整字段的 ActivityDetail，包括发起人名称、图片、审核记录和报名/候补人数。
     *
     * <p>不变量：不修改传入实体，不执行持久化操作。
     *
     * @param activity 活动实体
     * @param organizerName 发起人昵称
     * @param mediaFiles 活动关联的媒体文件列表
     * @param imageSortOrderProvider 图片排序函数
     * @param reviewRecords 审核记录 DTO 列表
     * @param registeredCount 已报名人数
     * @param occupiedCount 已占用名额数
     * @param waitingCount 候补人数
     * @return 活动详情 DTO
     */
    public ActivityDtos.ActivityDetail toActivityDetail(
            Activity activity,
            String organizerName,
            Collection<MediaFile> mediaFiles,
            Function<UUID, Integer> imageSortOrderProvider,
            List<ActivityDtos.ReviewRecord> reviewRecords,
            int registeredCount,
            int occupiedCount,
            int waitingCount) {
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
        dto.setFeeAmount(activity.getFeeAmount() != null ? activity.getFeeAmount() : java.math.BigDecimal.ZERO);
        dto.setReviewStatus(activity.getReviewStatus());
        dto.setRuntimeStatus(activity.getRuntimeStatus());
        dto.setRegisteredCount(registeredCount);
        dto.setOccupiedCount(occupiedCount);
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
        dto.setWaitingCount(waitingCount);
        dto.setAiContentReview(aiContentReviewSnapshotMapper.fromJson(activity.getAiContentReviewJson()));
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

    private String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String formatDraftInstant(Instant instant) {
        return ActivityDraftPlaceholders.isTimePlaceholder(instant) ? null : formatInstant(instant);
    }

    private String toDraftTitle(String title) {
        return ActivityDraftPlaceholders.isTitlePlaceholder(title) ? null : title;
    }

    private Integer toDraftCapacity(Integer capacity) {
        return ActivityDraftPlaceholders.isCapacityPlaceholder(capacity) ? null : capacity;
    }
}

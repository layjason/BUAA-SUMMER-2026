package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
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
public class ActivityDraftMapper {

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
        dto.setTitle(activity.getTitle());
        dto.setReviewStatus(activity.getReviewStatus());
        dto.setUpdatedAt(formatInstant(activity.getUpdatedAt()));
        dto.setCreatedAt(formatInstant(activity.getCreatedAt()));
        return dto;
    }

    public ActivityDtos.ActivityDraftDetail toDraftDetail(
            Activity activity, Collection<MediaFile> mediaFiles, Function<String, Integer> imageSortOrderProvider) {
        ActivityDtos.ActivityDraftDetail dto = new ActivityDtos.ActivityDraftDetail();
        dto.setActivityId(activity.getActivityId());
        dto.setTitle(activity.getTitle());
        dto.setTags(activity.getTags() == null ? List.of() : List.copyOf(activity.getTags()));
        dto.setIntroduction(activity.getIntroduction());
        dto.setStartAt(formatInstant(activity.getStartAt()));
        dto.setEndAt(formatInstant(activity.getEndAt()));
        dto.setLocation(toLocationInfo(activity));
        dto.setSafetyNotice(activity.getSafetyNotice());
        dto.setCapacity(activity.getCapacity());
        dto.setRegistrationDeadline(formatInstant(activity.getRegistrationDeadline()));
        dto.setFeeAmount(activity.getFeeAmount());
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
        CommonDtos.MediaFile dto = new CommonDtos.MediaFile();
        dto.setMediaId(mediaFile.getMediaId());
        dto.setFileName(mediaFile.getFileName());
        dto.setContentType(mediaFile.getContentType());
        dto.setSizeBytes(mediaFile.getSizeBytes());
        dto.setUsage(mediaFile.getUsage());
        dto.setUrl(mediaFile.getUrl());
        dto.setUploadedAt(formatInstant(mediaFile.getUploadedAt()));
        return dto;
    }

    private String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}

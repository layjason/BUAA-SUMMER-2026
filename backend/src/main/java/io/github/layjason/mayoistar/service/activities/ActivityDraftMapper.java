package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * 解析草稿请求中的地点信息。
     *
     * <p>前置条件：location 可以为空；非空时应满足 LocationInfo 结构。
     *
     * <p>后置条件：返回可供业务层使用的地点对象；结构非法时抛出参数异常。
     *
     * <p>不变量：该方法只做内存转换，不访问数据库。
     *
     * @param location 活动地点请求对象
     * @return 解析后的地点信息
     */
    public CommonDtos.LocationInfo toLocationInfo(Object location) {
        if (location == null) {
            return null;
        }
        if (!(location instanceof Map<?, ?> locationMap)) {
            throw new IllegalArgumentException("活动地点信息格式不合法");
        }
        CommonDtos.LocationInfo locationInfo = new CommonDtos.LocationInfo();
        locationInfo.setCity(stringValue(locationMap.get("city")));
        locationInfo.setAddress(stringValue(locationMap.get("address")));
        locationInfo.setPlaceName(stringValue(locationMap.get("placeName")));
        Object pointObject = locationMap.get("point");
        if (pointObject instanceof Map<?, ?> pointMap) {
            CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
            point.setLongitude(doubleValue(pointMap.get("longitude")));
            point.setLatitude(doubleValue(pointMap.get("latitude")));
            locationInfo.setPoint(point);
        }
        return locationInfo;
    }

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

    private CommonDtos.LocationInfo toLocationInfo(Activity activity) {
        if (activity.getPointLon() == null
                && activity.getPointLat() == null
                && activity.getCity() == null
                && activity.getAddress() == null
                && activity.getPlaceName() == null) {
            return null;
        }
        CommonDtos.GeoPoint point = null;
        if (activity.getPointLon() != null || activity.getPointLat() != null) {
            point = new CommonDtos.GeoPoint();
            point.setLongitude(activity.getPointLon());
            point.setLatitude(activity.getPointLat());
        }
        CommonDtos.LocationInfo dto = new CommonDtos.LocationInfo();
        dto.setPoint(point);
        dto.setCity(activity.getCity());
        dto.setAddress(activity.getAddress());
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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}

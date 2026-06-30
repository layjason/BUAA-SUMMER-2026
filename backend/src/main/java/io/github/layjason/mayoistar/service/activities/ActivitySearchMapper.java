package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 活动搜索响应映射器。
 *
 * <p>类职责：将 Activity 实体转换为搜索列表和地图点位 DTO。
 *
 * <p>类不变量：映射过程不访问数据库，不修改实体状态。
 */
@Component
public class ActivitySearchMapper {

    /**
     * 转换活动摘要。
     *
     * <p>前置条件：activity 不为 null。
     *
     * <p>后置条件：返回 DTO 包含搜索列表需要展示的活动基础字段。
     *
     * <p>不变量：封面图由后续媒体功能补齐，当前保持为空。
     *
     * @param activity 活动实体
     * @return 活动摘要 DTO
     */
    public ActivityDtos.ActivitySummary toSummary(Activity activity) {
        ActivityDtos.ActivitySummary summary = new ActivityDtos.ActivitySummary();
        summary.setActivityId(activity.getActivityId());
        summary.setTitle(activity.getTitle());
        summary.setTags(nullSafeTags(activity.getTags()));
        summary.setStartAt(formatInstant(activity.getStartAt()));
        summary.setEndAt(formatInstant(activity.getEndAt()));
        summary.setLocation(toLocation(activity));
        summary.setFeeAmount(activity.getFeeAmount());
        summary.setReviewStatus(activity.getReviewStatus());
        summary.setRuntimeStatus(activity.getRuntimeStatus());
        summary.setRegisteredCount(0);
        summary.setCapacity(activity.getCapacity());
        return summary;
    }

    /**
     * 转换地图点位。
     *
     * <p>前置条件：activity 不为 null，且调用方已保证经纬度存在。
     *
     * <p>后置条件：返回 DTO 包含地图渲染所需的活动标识、标题、点位和开始时间。
     *
     * <p>不变量：映射不重新计算活动状态。
     *
     * @param activity 活动实体
     * @return 活动地图点位 DTO
     */
    public ActivityDtos.ActivityMapPoint toMapPoint(Activity activity) {
        ActivityDtos.ActivityMapPoint point = new ActivityDtos.ActivityMapPoint();
        point.setActivityId(activity.getActivityId());
        point.setTitle(activity.getTitle());
        point.setPoint(toGeoPoint(activity));
        point.setRuntimeStatus(activity.getRuntimeStatus());
        point.setStartAt(formatInstant(activity.getStartAt()));
        return point;
    }

    /**
     * 转换地点信息。
     *
     * <p>前置条件：activity 不为 null。
     *
     * <p>后置条件：返回包含点位、城市、地址、地点名称的 LocationInfo。
     *
     * <p>不变量：经纬度缺失时仅 point 字段为空，其它地点字段仍按实体值返回。
     */
    private CommonDtos.LocationInfo toLocation(Activity activity) {
        CommonDtos.LocationInfo location = new CommonDtos.LocationInfo();
        location.setPoint(toGeoPoint(activity));
        location.setCity(activity.getCity());
        location.setAddress(activity.getAddress());
        location.setPlaceName(activity.getPlaceName());
        return location;
    }

    /**
     * 转换地理坐标。
     *
     * <p>前置条件：activity 不为 null。
     *
     * <p>后置条件：经纬度完整时返回 GeoPoint，否则返回 null。
     *
     * <p>不变量：不对坐标做修正或投影转换。
     */
    private CommonDtos.GeoPoint toGeoPoint(Activity activity) {
        if (activity.getPointLon() == null || activity.getPointLat() == null) {
            return null;
        }
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(activity.getPointLon());
        point.setLatitude(activity.getPointLat());
        return point;
    }

    /**
     * 格式化时间。
     *
     * <p>前置条件：instant 可为空。
     *
     * <p>后置条件：非空时间转换为 ISO-8601 字符串。
     *
     * <p>不变量：不改变时区语义，Instant 以 UTC 表示。
     */
    private String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    /**
     * 归一化标签列表。
     *
     * <p>前置条件：tags 可为空。
     *
     * <p>后置条件：返回非空列表。
     *
     * <p>不变量：不改变标签顺序。
     */
    private List<String> nullSafeTags(List<String> tags) {
        return tags == null ? List.of() : tags;
    }
}

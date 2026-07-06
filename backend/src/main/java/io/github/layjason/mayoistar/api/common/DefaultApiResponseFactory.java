package io.github.layjason.mayoistar.api.common;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * API 契约骨架默认响应工厂。
 *
 * <p>类职责：为尚未接入业务服务的 Controller 生成符合 OpenAPI 响应 Schema 的占位数据。
 *
 * <p>类不变量：所有 JSON 占位响应均使用统一成功包装，不访问数据库、不调用外部服务。
 */
@Component
public class DefaultApiResponseFactory {

    private static final String NOW = "2026-06-29T08:00:00Z";

    /* ========== 通用 ========== */

    /**
     * 创建空分页结果响应。
     *
     * <p>前置条件：调用方需要分页占位响应。
     *
     * <p>后置条件：返回第 1 页、每页 20 条、无数据的分页结果。
     *
     * <p>不变量：该方法不访问数据库，不修改外部状态。
     *
     * @param <T> 分页条目类型
     * @return 空分页响应
     */
    public <T> ResponseEntity<ApiResponse<PageResult<T>>> emptyPage() {
        return ResponseEntity.ok(ApiResponse.success(PageResult.empty()));
    }

    /* ========== 活动 ========== */

    /**
     * 创建活动草稿详情占位响应。
     *
     * <p>前置条件：调用方已在 Controller 层判断无用户登录。
     *
     * <p>后置条件：返回草稿状态的占位 ActivityDraftDetail。
     *
     * <p>不变量：不访问数据库。
     *
     * @return 活动草稿详情响应
     */
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> activityDraftDetail() {
        ActivityDtos.ActivityDraftDetail dto = new ActivityDtos.ActivityDraftDetail();
        dto.setActivityId("activity-placeholder");
        dto.setTags(List.of());
        dto.setImages(List.of());
        dto.setReviewStatus(ActivityReviewStatus.draft);
        dto.setUpdatedAt(NOW);
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * 创建活动详情占位响应。
     *
     * <p>前置条件：调用方在未登录状态下需要 ActivityDetail 响应。
     *
     * <p>后置条件：返回完整字段占位的 ActivityDetail。
     *
     * <p>不变量：不访问数据库。
     *
     * @return 活动详情响应
     */
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> activityDetail() {
        ActivityDtos.ActivityDetail dto = new ActivityDtos.ActivityDetail();
        dto.setActivityId("activity-placeholder");
        dto.setTitle("默认活动");
        dto.setTags(List.of());
        dto.setStartAt(NOW);
        dto.setEndAt(NOW);
        dto.setLocation(location());
        dto.setReviewStatus(ActivityReviewStatus.approved);
        dto.setRuntimeStatus(ActivityRuntimeStatus.notStarted);
        dto.setRegisteredCount(0);
        dto.setCapacity(20);
        dto.setIntroduction("默认活动简介");
        dto.setSafetyNotice("默认安全须知");
        dto.setRegistrationDeadline(NOW);
        dto.setOrganizerId("user-placeholder");
        dto.setOrganizerName("MayoiStar");
        dto.setImages(List.of());
        dto.setWaitingCount(0);
        dto.setManualReviewRequired(false);
        dto.setReviewRecords(List.of());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * 创建签到二维码占位响应。
     *
     * <p>前置条件：尚未接入真实签到服务。
     *
     * <p>后置条件：返回占位 token 和超时时间。
     *
     * <p>不变量：不调用外部服务。
     *
     * @return 签到二维码响应
     */
    public ResponseEntity<ApiResponse<ActivityDtos.CheckInQrCode>> checkInQrCode() {
        ActivityDtos.CheckInQrCode dto = new ActivityDtos.CheckInQrCode();
        dto.setActivityId("activity-placeholder");
        dto.setQrCodeToken("qr-placeholder");
        dto.setExpiresAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * 创建签到记录占位响应。
     *
     * <p>前置条件：尚未接入真实签到服务。
     *
     * <p>后置条件：返回已签到状态的占位记录。
     *
     * <p>不变量：不访问数据库。
     *
     * @return 签到记录响应
     */
    public ResponseEntity<ApiResponse<ActivityDtos.CheckInRecord>> checkInRecord() {
        ActivityDtos.CheckInRecord dto = new ActivityDtos.CheckInRecord();
        dto.setRegistrationId("registration-placeholder");
        dto.setUserId("user-placeholder");
        dto.setNickname("MayoiStar");
        dto.setRegistrationStatus(RegistrationStatus.checkedIn);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * 创建活动总结帖占位响应。
     *
     * <p>前置条件：尚未接入活动总结服务。
     *
     * <p>后置条件：返回默认标题与内容的占位总结。
     *
     * <p>不变量：不访问数据库。
     *
     * @return 活动总结帖响应
     */
    public ResponseEntity<ApiResponse<ActivityDtos.ActivitySummaryPost>> activitySummaryPost() {
        ActivityDtos.ActivitySummaryPost dto = new ActivityDtos.ActivitySummaryPost();
        dto.setSummaryId("summary-placeholder");
        dto.setActivityId("activity-placeholder");
        dto.setTitle("默认总结");
        dto.setContent("默认总结内容");
        dto.setImages(List.of());
        dto.setImageTags(List.of());
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * 创建活动评价占位响应。
     *
     * <p>前置条件：尚未接入活动评价服务。
     *
     * <p>后置条件：返回 5 星评分占位评价。
     *
     * <p>不变量：不访问数据库。
     *
     * @return 活动评价响应
     */
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityReview>> activityReview() {
        ActivityDtos.ActivityReview dto = new ActivityDtos.ActivityReview();
        dto.setReviewId("review-placeholder");
        dto.setActivityId("activity-placeholder");
        dto.setUserId("user-placeholder");
        dto.setRating(5);
        dto.setTags(List.of());
        dto.setCreatedAt(NOW);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /* ========== 内部辅助方法 ========== */

    /**
     * 创建地点信息占位数据。
     *
     * <p>前置条件：调用方需要 LocationInfo 结构。
     *
     * <p>后置条件：返回地理坐标、城市和地址字段。
     *
     * <p>不变量：不调用地图或定位服务。
     */
    private CommonDtos.LocationInfo location() {
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(116.397);
        point.setLatitude(39.907);

        CommonDtos.LocationInfo loc = new CommonDtos.LocationInfo();
        loc.setPoint(point);
        loc.setCity("北京");
        loc.setAddress("默认地址");
        loc.setPlaceName("默认地点");
        return loc;
    }
}

package io.github.layjason.mayoistar.service.activities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.repository.activities.ActivityRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * 活动搜索服务测试。
 *
 * <p>类职责：验证活动搜索、高级筛选、地图点位和分页逻辑。
 *
 * <p>类不变量：测试使用 Mock Repository，不访问真实数据库，也不调用外部服务。
 */
@ExtendWith(MockitoExtension.class)
class ActivitySearchServiceTests {

    @Mock
    private ActivityRepository activityRepository;

    private ActivitySearchService service;

    /**
     * 初始化待测服务。
     *
     * <p>前置条件：Mockito 已创建 Repository Mock。
     *
     * <p>后置条件：每个测试用例获得新的服务实例。
     *
     * <p>不变量：Mapper 使用真实实现以覆盖 DTO 映射逻辑。
     */
    @BeforeEach
    void setUp() {
        service = new ActivitySearchService(activityRepository, new ActivitySearchMapper());
    }

    /**
     * 验证搜索结果分页和摘要映射。
     *
     * <p>前置条件：Repository 返回两个已按开始时间排序的活动。
     *
     * <p>后置条件：第一页只包含第一条活动，总数和页数正确。
     *
     * <p>不变量：费用字段仅原样进入 DTO，不触发支付语义。
     */
    @Test
    void searchReturnsPagedSummaries() {
        when(activityRepository.findAll(anySpecification(), any(Sort.class)))
                .thenReturn(List.of(
                        activity("activity-1", "桌游夜", 116.397, 39.908), activity("activity-2", "徒步", 116.5, 39.9)));

        PageResult<ActivityDtos.ActivitySummary> result = service.search(new ActivitySearchCriteria(
                "桌游",
                List.of("桌游"),
                "北京",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                BigDecimal.ZERO,
                BigDecimal.TEN,
                null,
                null,
                null,
                1,
                1));

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getItems()).hasSize(1);
        ActivityDtos.ActivitySummary summary = result.getItems().getFirst();
        assertThat(summary.getActivityId()).isEqualTo("activity-1");
        assertThat(summary.getTitle()).isEqualTo("桌游夜");
        assertThat(summary.getLocation().getPoint().getLongitude()).isEqualTo(116.397);
        assertThat(summary.getRegisteredCount()).isZero();
        assertThat(summary.getFeeAmount()).isEqualByComparingTo("5.00");
    }

    /**
     * 验证距离筛选。
     *
     * <p>前置条件：Repository 返回一个近距离活动和一个远距离活动。
     *
     * <p>后置条件：只返回半径内活动。
     *
     * <p>不变量：距离筛选在服务层完成，不依赖数据库空间函数。
     */
    @Test
    void searchFiltersByDistance() {
        when(activityRepository.findAll(anySpecification(), any(Sort.class)))
                .thenReturn(
                        List.of(activity("near", "近处活动", 116.397, 39.908), activity("far", "远处活动", 121.473, 31.230)));

        PageResult<ActivityDtos.ActivitySummary> result = service.search(
                new ActivitySearchCriteria(null, null, null, null, null, null, null, 39.908, 116.397, 1000, 1, 20));

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getItems())
                .extracting(ActivityDtos.ActivitySummary::getActivityId)
                .containsExactly("near");
    }

    /**
     * 验证地图点位只返回有坐标活动。
     *
     * <p>前置条件：Repository 同时返回有坐标和无坐标活动。
     *
     * <p>后置条件：地图点位列表排除无坐标活动。
     *
     * <p>不变量：地图点位 DTO 不包含活动详情或报名状态。
     */
    @Test
    void mapPointsReturnActivitiesWithGeoPointOnly() {
        when(activityRepository.findAll(anySpecification(), any(Sort.class)))
                .thenReturn(List.of(
                        activity("with-point", "有坐标", 116.397, 39.908), activity("without-point", "无坐标", null, null)));

        List<ActivityDtos.ActivityMapPoint> points = service.mapPoints(
                new ActivitySearchCriteria(null, null, null, null, null, null, null, null, null, null, 1, 20));

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().getActivityId()).isEqualTo("with-point");
        assertThat(points.getFirst().getPoint().getLatitude()).isEqualTo(39.908);
    }

    /**
     * 验证分页参数归一化。
     *
     * <p>前置条件：Repository 返回超过默认每页数量的活动列表。
     *
     * <p>后置条件：非法分页参数回退到第一页和默认每页数量。
     *
     * <p>不变量：分页不会修改原始 Repository 返回列表。
     */
    @Test
    void searchNormalizesInvalidPagination() {
        List<Activity> activities = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(index -> activity("activity-" + index, "活动" + index, 116.0 + index, 39.0))
                .toList();
        when(activityRepository.findAll(anySpecification(), any(Sort.class))).thenReturn(activities);

        PageResult<ActivityDtos.ActivitySummary> result = service.search(
                new ActivitySearchCriteria(null, null, null, null, null, null, null, null, null, null, 0, 0));

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getItems()).hasSize(20);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    /**
     * 创建测试活动。
     *
     * <p>前置条件：activityId 和 title 不为空。
     *
     * <p>后置条件：返回审核通过且未下架的活动实体。
     *
     * <p>不变量：该实体仅用于测试，不写入数据库。
     */
    private Activity activity(String activityId, String title, Double longitude, Double latitude) {
        return Activity.builder()
                .activityId(activityId)
                .organizerId("user-1")
                .title(title)
                .tags(List.of("桌游", "室内"))
                .introduction(title + "介绍")
                .startAt(Instant.parse("2026-07-10T10:00:00Z"))
                .endAt(Instant.parse("2026-07-10T12:00:00Z"))
                .pointLon(longitude)
                .pointLat(latitude)
                .city("北京")
                .address("海淀区")
                .placeName("活动室")
                .capacity(20)
                .feeAmount(new BigDecimal("5.00"))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.registering)
                .createdAt(Instant.parse("2026-06-30T00:00:00Z"))
                .updatedAt(Instant.parse("2026-06-30T00:00:00Z"))
                .build();
    }

    /**
     * 匹配任意活动 Specification。
     *
     * <p>前置条件：Mockito 正在匹配 Repository 调用。
     *
     * <p>后置条件：返回类型安全的 Specification 匹配器。
     *
     * <p>不变量：该方法只用于测试桩配置。
     */
    @SuppressWarnings("unchecked")
    private Specification<Activity> anySpecification() {
        return any(Specification.class);
    }
}

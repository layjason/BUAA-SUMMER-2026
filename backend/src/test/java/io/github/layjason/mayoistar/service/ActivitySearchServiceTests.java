package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.service.activities.ActivityRegistrationCountService;
import io.github.layjason.mayoistar.service.activities.ActivityRegistrationCounts;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * 活动搜索服务单元测试。
 *
 * <p>类职责：验证活动搜索、高级筛选、地图点位和分页逻辑。
 *
 * <p>类不变量：测试使用 Mock Repository 模拟真实分页行为，不访问真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class ActivitySearchServiceTests {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityRegistrationCountService activityRegistrationCountService;

    private ActivitySearchService service;

    @BeforeEach
    void setUp() {
        service = new ActivitySearchService(activityRepository, activityRegistrationCountService);
    }

    /**
     * 验证搜索结果分页和摘要映射。
     *
     * <p>前置条件：Repository 返回两个活动，分页参数 pageSize=1 时只返回第一条。
     *
     * <p>后置条件：每页大小 1 时返回 1 条摘要，总数 2，共 2 页。
     */
    @Test
    void searchReturnsPagedSummaries() {
        List<Activity> all =
                List.of(activity("activity-1", "桌游夜", 116.397, 39.908), activity("activity-2", "徒步", 116.5, 39.9));
        // 模拟 page=1, pageSize=1: 返回第 0 页，内容仅第一条，总数 2
        Page<Activity> page = new PageImpl<>(List.of(all.get(0)), PageRequest.of(0, 1), 2);
        when(activityRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(page);
        when(activityRegistrationCountService.countByActivityIds(List.of("activity-1")))
                .thenReturn(Map.of("activity-1", new ActivityRegistrationCounts(2, 3, 1)));

        PageResult<ActivityDtos.ActivitySummary> result = service.search(new ActivitySearchService.SearchCriteria(
                "桌游",
                List.of("桌游"),
                "北京",
                "2026-07-01T00:00:00Z",
                "2026-08-01T00:00:00Z",
                0D,
                10D,
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
        assertThat(summary.getRegisteredCount()).isEqualTo(2);
        assertThat(summary.getOccupiedCount()).isEqualTo(3);
        assertThat(summary.getFeeAmount()).isEqualByComparingTo("5.00");
    }

    /**
     * 验证距离筛选。
     *
     * <p>前置条件：Repository 返回两个活动，服务在内存中按距离筛选。
     *
     * <p>后置条件：距离内活动保留（near），超出抛弃（far）。PageResult.total 仍反映数据库总数。
     */
    @Test
    void searchFiltersByDistance() {
        List<Activity> all =
                List.of(activity("near", "近处活动", 116.397, 39.908), activity("far", "远处活动", 121.473, 31.230));
        Page<Activity> page = new PageImpl<>(all, PageRequest.of(0, 20), 2);
        when(activityRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(page);
        when(activityRegistrationCountService.countByActivityIds(List.of("near", "far")))
                .thenReturn(Map.of(
                        "near", new ActivityRegistrationCounts(1, 1, 0),
                        "far", new ActivityRegistrationCounts(2, 2, 0)));

        PageResult<ActivityDtos.ActivitySummary> result = service.search(new ActivitySearchService.SearchCriteria(
                null, null, null, null, null, null, null, 39.908, 116.397, 1000, 1, 20));

        // 距离筛选是内存过滤，PageResult.total 来自数据库总数（2）
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getItems())
                .extracting(ActivityDtos.ActivitySummary::getActivityId)
                .containsExactly("near");
    }

    /**
     * 验证地图点位只返回有坐标活动。
     *
     * <p>前置条件：Repository 同时返回有坐标和无坐标活动。
     *
     * <p>后置条件：地图点位排除无坐标的活动摘要。
     */
    @Test
    void mapPointsReturnActivitiesWithGeoPointOnly() {
        List<Activity> all =
                List.of(activity("with-point", "有坐标", 116.397, 39.908), activity("without-point", "无坐标", null, null));
        Page<Activity> page = new PageImpl<>(all, PageRequest.of(0, 20), 2);
        when(activityRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(page);
        when(activityRegistrationCountService.countByActivityIds(List.of("with-point", "without-point")))
                .thenReturn(Map.of(
                        "with-point", ActivityRegistrationCounts.zero(),
                        "without-point", ActivityRegistrationCounts.zero()));

        List<ActivityDtos.ActivityMapPoint> points = service.mapPoints(new ActivitySearchService.SearchCriteria(
                null, null, null, null, null, null, null, null, null, null, 1, 20));

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().getActivityId()).isEqualTo("with-point");
        assertThat(points.getFirst().getPoint().getLatitude()).isEqualTo(39.908);
    }

    /**
     * 验证分页参数归一化。
     *
     * <p>前置条件：传入非法分页参数 (page=0, pageSize=0)。
     *
     * <p>后置条件：服务归一化为 (page=1, pageSize=20)，依靠数据库分页返回正确页数和条数。
     */
    @Test
    void searchNormalizesInvalidPagination() {
        List<Activity> all = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(index -> activity("activity-" + index, "活动" + index, 116.0 + index * 0.01, 39.0))
                .toList();
        // 模拟第 0 页 pageSize=20: 返回前 20 条，总数 25
        Page<Activity> page = new PageImpl<>(all.subList(0, 20), PageRequest.of(0, 20), 25);
        when(activityRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(page);
        when(activityRegistrationCountService.countByActivityIds(
                        all.subList(0, 20).stream().map(Activity::getActivityId).toList()))
                .thenReturn(Map.of());

        PageResult<ActivityDtos.ActivitySummary> result = service.search(new ActivitySearchService.SearchCriteria(
                null, null, null, null, null, null, null, null, null, null, 0, 0));

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

    @SuppressWarnings("unchecked")
    private Specification<Activity> anySpecification() {
        return any(Specification.class);
    }
}

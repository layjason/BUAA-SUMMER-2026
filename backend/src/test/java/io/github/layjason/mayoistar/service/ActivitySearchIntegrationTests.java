package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动搜索集成测试。
 *
 * <p>类职责：验证活动搜索服务与 H2 测试数据库、JPA Specification 的真实协作。
 *
 * <p>类不变量：测试只使用 H2 内存数据库，不访问外部服务。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActivitySearchIntegrationTests {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivitySearchService activitySearchService;

    /**
     * 准备测试数据。
     *
     * <p>前置条件：H2 数据库已由 Flyway 创建表结构。
     *
     * <p>后置条件：数据库中存在一个用户和三条不同可见性的活动。
     *
     * <p>不变量：事务在测试结束后回滚，不污染其它测试。
     */
    @BeforeEach
    void setUp() {
        userRepository.save(User.builder()
                .userId("search-user")
                .email("search@example.com")
                .nickname("搜索用户")
                .passwordHash("hashed")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.parse("2026-06-30T00:00:00Z"))
                .updatedAt(Instant.parse("2026-06-30T00:00:00Z"))
                .build());
        activityRepository.save(activity(
                "search-board-game",
                "桌游夜",
                List.of("桌游", "室内"),
                "北京",
                116.397,
                39.908,
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering));
        activityRepository.save(activity(
                "search-hiking",
                "周末徒步",
                List.of("户外"),
                "北京",
                116.401,
                39.910,
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering));
        activityRepository.save(activity(
                "search-draft",
                "草稿活动",
                List.of("桌游"),
                "北京",
                116.397,
                39.908,
                ActivityReviewStatus.draft,
                ActivityRuntimeStatus.registering));
        activityRepository.flush();
    }

    /**
     * 验证搜索筛选可在真实 H2 数据库中执行。
     *
     * <p>前置条件：数据库存在活动标签和公开活动数据。
     *
     * <p>后置条件：关键词、标签、城市、费用、距离筛选共同命中目标活动。
     *
     * <p>不变量：草稿活动不会进入公开搜索结果。
     */
    @Test
    void searchWorksAgainstH2Database() {
        PageResult<ActivityDtos.ActivitySummary> result =
                activitySearchService.search(new ActivitySearchService.SearchCriteria(
                        "桌游",
                        List.of("桌游"),
                        "北京",
                        "2026-07-01T00:00:00Z",
                        "2026-07-31T23:59:59Z",
                        0D,
                        10D,
                        39.908,
                        116.397,
                        1000,
                        1,
                        20));

        assertThat(result.getItems())
                .extracting(ActivityDtos.ActivitySummary::getActivityId)
                .containsExactly("search-board-game");
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    /**
     * 验证地图点位接口复用搜索筛选。
     *
     * <p>前置条件：数据库存在具备经纬度的公开活动。
     *
     * <p>后置条件：返回匹配城市和距离的地图点位。
     *
     * <p>不变量：地图接口不返回草稿活动。
     */
    @Test
    void mapPointsWorkAgainstH2Database() {
        List<ActivityDtos.ActivityMapPoint> points =
                activitySearchService.mapPoints(new ActivitySearchService.SearchCriteria(
                        null, null, "北京", null, null, null, null, 39.908, 116.397, 1000, 1, 20));

        assertThat(points)
                .extracting(ActivityDtos.ActivityMapPoint::getActivityId)
                .containsExactly("search-board-game", "search-hiking");
    }

    /**
     * 创建测试活动。
     *
     * <p>前置条件：activityId、title、tags、city 不为空。
     *
     * <p>后置条件：返回可持久化的活动实体。
     *
     * <p>不变量：费用字段仅用于筛选测试，不表达支付状态。
     */
    private Activity activity(
            String activityId,
            String title,
            List<String> tags,
            String city,
            Double longitude,
            Double latitude,
            ActivityReviewStatus reviewStatus,
            ActivityRuntimeStatus runtimeStatus) {
        return Activity.builder()
                .activityId(activityId)
                .organizerId("search-user")
                .title(title)
                .tags(tags)
                .introduction(title + "介绍")
                .startAt(Instant.parse("2026-07-10T10:00:00Z"))
                .endAt(Instant.parse("2026-07-10T12:00:00Z"))
                .pointLon(longitude)
                .pointLat(latitude)
                .city(city)
                .address("海淀区")
                .placeName("活动室")
                .safetyNotice("注意安全")
                .capacity(20)
                .feeAmount(new BigDecimal("5.00"))
                .registrationDeadline(Instant.parse("2026-07-09T10:00:00Z"))
                .reviewStatus(reviewStatus)
                .runtimeStatus(runtimeStatus)
                .createdAt(Instant.parse("2026-06-30T00:00:00Z"))
                .updatedAt(Instant.parse("2026-06-30T00:00:00Z"))
                .build();
    }
}

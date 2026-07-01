package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.activities.ActivityRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(ActivitySearchService.class)
class ActivitySearchServiceTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivitySearchService activitySearchService;

    private String organizerId;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();
        userRepository.deleteAll();
        organizerId = UUID.randomUUID().toString();
        userRepository.save(User.builder()
                .userId(organizerId)
                .email("organizer-" + organizerId + "@example.com")
                .nickname("organizer-" + organizerId.substring(0, 8))
                .passwordHash("password-hash")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .activatedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .createdAt(Instant.parse("2026-07-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build());
    }

    @Test
    @DisplayName("按关键词、城市、标签和费用筛选活动")
    void shouldSearchActivitiesByFilters() {
        activityRepository.save(activity(
                "北京桌游夜",
                List.of("桌游", "社交"),
                "轻松破冰",
                "北京",
                BigDecimal.valueOf(30),
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-01T10:00:00Z"),
                116.397,
                39.907));
        activityRepository.save(activity(
                "上海桌游夜",
                List.of("桌游"),
                "轻松破冰",
                "上海",
                BigDecimal.valueOf(30),
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-02T10:00:00Z"),
                121.473,
                31.230));
        activityRepository.save(activity(
                "北京摄影漫步",
                List.of("摄影"),
                "城市影像",
                "北京",
                BigDecimal.valueOf(120),
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-03T10:00:00Z"),
                116.397,
                39.907));

        var result = activitySearchService.search(new ActivitySearchService.SearchCriteria(
                "桌游", List.of("社交"), "北京", null, null, 0D, 50D, null, null, null, 1, 20));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getTitle()).isEqualTo("北京桌游夜");
        assertThat(result.getItems().getFirst().getLocation().getCity()).isEqualTo("北京");
    }

    @Test
    @DisplayName("只返回审核通过且未下架的活动")
    void shouldOnlyReturnApprovedVisibleActivities() {
        activityRepository.save(activity(
                "可见活动",
                List.of("运动"),
                "羽毛球",
                "北京",
                BigDecimal.ZERO,
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-01T10:00:00Z"),
                116.397,
                39.907));
        activityRepository.save(activity(
                "待审核活动",
                List.of("运动"),
                "羽毛球",
                "北京",
                BigDecimal.ZERO,
                ActivityReviewStatus.pending,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-01T10:00:00Z"),
                116.397,
                39.907));
        activityRepository.save(activity(
                "下架活动",
                List.of("运动"),
                "羽毛球",
                "北京",
                BigDecimal.ZERO,
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.takenDown,
                Instant.parse("2026-08-01T10:00:00Z"),
                116.397,
                39.907));

        var result = activitySearchService.search(new ActivitySearchService.SearchCriteria(
                null, null, null, null, null, null, null, null, null, null, 1, 20));

        assertThat(result.getItems()).extracting("title").containsExactly("可见活动");
    }

    @Test
    @DisplayName("按距离筛选活动")
    void shouldFilterByDistance() {
        activityRepository.save(activity(
                "近处活动",
                List.of("户外"),
                "附近",
                "北京",
                BigDecimal.ZERO,
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-01T10:00:00Z"),
                116.397,
                39.907));
        activityRepository.save(activity(
                "远处活动",
                List.of("户外"),
                "较远",
                "上海",
                BigDecimal.ZERO,
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-01T10:00:00Z"),
                121.473,
                31.230));

        var result = activitySearchService.search(new ActivitySearchService.SearchCriteria(
                null, null, null, null, null, null, null, 39.907, 116.397, 5_000, 1, 20));

        assertThat(result.getItems()).extracting("title").containsExactly("近处活动");
    }

    @Test
    @DisplayName("地图点位复用搜索结果并排除无坐标活动")
    void shouldListMapPointsFromSearchResult() {
        activityRepository.save(activity(
                "北京桌游点位",
                List.of("桌游"),
                "轻松破冰",
                "北京",
                BigDecimal.ZERO,
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-01T10:00:00Z"),
                116.397,
                39.907));
        activityRepository.save(activity(
                "无坐标活动",
                List.of("桌游"),
                "轻松破冰",
                "北京",
                BigDecimal.ZERO,
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null));
        activityRepository.save(activity(
                "上海桌游点位",
                List.of("桌游"),
                "轻松破冰",
                "上海",
                BigDecimal.ZERO,
                ActivityReviewStatus.approved,
                ActivityRuntimeStatus.registering,
                Instant.parse("2026-08-03T10:00:00Z"),
                121.473,
                31.230));

        var result = activitySearchService.mapPoints(new ActivitySearchService.SearchCriteria(
                "桌游", List.of("桌游"), "北京", null, null, null, null, null, null, null, 1, 20));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("北京桌游点位");
        assertThat(result.getFirst().getPoint().getLongitude()).isEqualTo(116.397);
        assertThat(result.getFirst().getPoint().getLatitude()).isEqualTo(39.907);
    }

    private Activity activity(
            String title,
            List<String> tags,
            String introduction,
            String city,
            BigDecimal feeAmount,
            ActivityReviewStatus reviewStatus,
            ActivityRuntimeStatus runtimeStatus,
            Instant startAt,
            Double pointLon,
            Double pointLat) {
        return Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title(title)
                .tags(tags)
                .introduction(introduction)
                .startAt(startAt)
                .endAt(startAt.plusSeconds(7200))
                .pointLon(pointLon)
                .pointLat(pointLat)
                .city(city)
                .address(city + "地址")
                .placeName(city + "地点")
                .safetyNotice("注意安全")
                .capacity(20)
                .feeAmount(feeAmount)
                .registrationDeadline(startAt.minusSeconds(3600))
                .reviewStatus(reviewStatus)
                .runtimeStatus(runtimeStatus)
                .manualReviewRequired(false)
                .createdAt(Instant.parse("2026-07-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
    }
}

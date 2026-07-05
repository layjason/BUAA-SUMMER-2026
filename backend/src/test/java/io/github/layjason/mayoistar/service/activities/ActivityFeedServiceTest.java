package io.github.layjason.mayoistar.service.activities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import io.github.layjason.mayoistar.service.ActivitySearchService;
import io.github.layjason.mayoistar.service.ai.AiContentReviewSnapshotMapper;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({
    ActivityFeedService.class,
    ActivitySearchService.class,
    ActivityDtoMapper.class,
    ActivityFeedServiceTest.TestConfig.class
})
class ActivityFeedServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        MediaAccessService mediaAccessService() {
            return mock(MediaAccessService.class);
        }

        @Bean
        ActivityMediaQueryService activityMediaQueryService() {
            return mock(ActivityMediaQueryService.class);
        }

        @Bean
        ActivityRegistrationCountService activityRegistrationCountService() {
            ActivityRegistrationCountService mock = mock(ActivityRegistrationCountService.class);
            when(mock.countByActivityIds(anyCollection())).thenReturn(Map.of());
            return mock;
        }

        @Bean
        AiContentReviewSnapshotMapper aiContentReviewSnapshotMapper() {
            return mock(AiContentReviewSnapshotMapper.class);
        }
    }

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityFeedService activityFeedService;

    private String organizerId;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();
        userRepository.deleteAll();
        organizerId = createUser("organizer");
    }

    private String createUser(String nicknamePrefix) {
        String userId = UUID.randomUUID().toString();
        userRepository.save(User.builder()
                .userId(userId)
                .email(nicknamePrefix + "-" + userId + "@example.com")
                .nickname(nicknamePrefix + "-" + userId.substring(0, 8))
                .passwordHash("password-hash")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .activatedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        return userId;
    }

    /**
     * 创建审核通过且未下架的活动。
     */
    private Activity activity(String title, Instant createdAt) {
        Activity a = new Activity();
        a.setActivityId(UUID.randomUUID().toString());
        a.setOrganizerId(organizerId);
        a.setTitle(title);
        a.setReviewStatus(ActivityReviewStatus.approved);
        a.setRuntimeStatus(ActivityRuntimeStatus.ongoing);
        a.setCreatedAt(createdAt);
        a.setStartAt(Instant.now().plusSeconds(3600));
        a.setEndAt(Instant.now().plusSeconds(7200));
        a.setRegistrationDeadline(Instant.now().plusSeconds(1800));
        a.setCapacity(50);
        a.setSafetyNotice("安全须知");
        a.setIntroduction("简介");
        return a;
    }

    /**
     * 创建指定审核状态和运行时状态的活动。
     */
    private Activity activityWithStatus(ActivityReviewStatus reviewStatus, ActivityRuntimeStatus runtimeStatus) {
        Activity a = new Activity();
        a.setActivityId(UUID.randomUUID().toString());
        a.setOrganizerId(organizerId);
        a.setTitle("测试活动");
        a.setReviewStatus(reviewStatus);
        a.setRuntimeStatus(runtimeStatus);
        a.setCreatedAt(Instant.now());
        a.setStartAt(Instant.now().plusSeconds(3600));
        a.setEndAt(Instant.now().plusSeconds(7200));
        a.setRegistrationDeadline(Instant.now().plusSeconds(1800));
        a.setCapacity(50);
        a.setSafetyNotice("安全须知");
        a.setIntroduction("简介");
        return a;
    }

    /**
     * 创建带坐标的活动。
     */
    private Activity activityWithLocation(String title, double lat, double lon, Instant createdAt) {
        Activity a = activity(title, createdAt);
        a.setPointLat(lat);
        a.setPointLon(lon);
        return a;
    }

    /**
     * 创建基础 SearchCriteria，仅含分页参数。
     */
    private ActivitySearchService.SearchCriteria criteriaWithPage(Integer page, Integer pageSize) {
        return new ActivitySearchService.SearchCriteria(
                null, null, null, null, null, null, null, null, null, null, page, pageSize);
    }

    /**
     * 创建带坐标的 SearchCriteria，用于附近 Tab。
     */
    private ActivitySearchService.SearchCriteria criteriaWithLocation(
            Double lat, Double lon, Integer distanceMeters, Integer page, Integer pageSize) {
        return new ActivitySearchService.SearchCriteria(
                null, null, null, null, null, null, null, lat, lon, distanceMeters, page, pageSize);
    }

    @Test
    @DisplayName("最新 Tab 按创建时间降序排列")
    void shouldSortByCreatedAtDescForLatestTab() {
        Instant now = Instant.now();
        activityRepository.save(activity("旧活动", now.minusSeconds(7200)));
        activityRepository.save(activity("新活动", now));

        PageResult<ActivityDtos.ActivitySummary> result =
                activityFeedService.getFeed("latest", criteriaWithPage(1, 20));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("新活动");
        assertThat(result.getItems().get(1).getTitle()).isEqualTo("旧活动");
    }

    @Test
    @DisplayName("推荐 Tab 进行随机分页")
    void shouldPaginateRecommendedFeed() {
        for (int i = 0; i < 30; i++) {
            activityRepository.save(activity("活动" + i, Instant.now()));
        }

        PageResult<ActivityDtos.ActivitySummary> page1 =
                activityFeedService.getFeed("recommended", criteriaWithPage(1, 10));
        PageResult<ActivityDtos.ActivitySummary> page2 =
                activityFeedService.getFeed("recommended", criteriaWithPage(2, 10));

        assertThat(page1.getItems()).hasSize(10);
        assertThat(page2.getItems()).hasSize(10);
        assertThat(page1.getTotal()).isEqualTo(30);
        assertThat(page1.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("附近 Tab 按距离升序排列")
    void shouldSortByDistanceForNearbyTab() {
        Instant now = Instant.now();
        activityRepository.save(activityWithLocation("鸟巢", 39.9919, 116.3904, now));
        activityRepository.save(activityWithLocation("故宫", 39.9180, 116.3975, now));

        PageResult<ActivityDtos.ActivitySummary> result =
                activityFeedService.getFeed("nearby", criteriaWithLocation(39.9042, 116.3975, null, 1, 20));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("故宫");
        assertThat(result.getItems().get(1).getTitle()).isEqualTo("鸟巢");
    }

    @Test
    @DisplayName("附近 Tab 按 distanceMeters 过滤范围内活动")
    void shouldFilterByDistanceMetersForNearbyTab() {
        Instant now = Instant.now();
        // 故宫距离天安门约 1.5km，鸟巢距离天安门约 9.7km
        activityRepository.save(activityWithLocation("故宫", 39.9180, 116.3975, now));
        activityRepository.save(activityWithLocation("鸟巢", 39.9919, 116.3904, now));

        // 以天安门为中心，5km 半径
        PageResult<ActivityDtos.ActivitySummary> result =
                activityFeedService.getFeed("nearby", criteriaWithLocation(39.9042, 116.3975, 5000, 1, 20));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("故宫");
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("附近 Tab 无坐标时回退按最新排序")
    void shouldFallbackToLatestWhenNearbyWithoutCoordinates() {
        Instant now = Instant.now();
        activityRepository.save(activity("旧活动", now.minusSeconds(3600)));
        activityRepository.save(activity("新活动", now));

        PageResult<ActivityDtos.ActivitySummary> result =
                activityFeedService.getFeed("nearby", criteriaWithPage(1, 20));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("新活动");
    }

    @Test
    @DisplayName("仅返回审核通过且未下架的活动")
    void shouldOnlyReturnVisibleActivities() {
        activityRepository.save(activityWithStatus(ActivityReviewStatus.approved, ActivityRuntimeStatus.ongoing));
        activityRepository.save(activityWithStatus(ActivityReviewStatus.pending, ActivityRuntimeStatus.ongoing));
        activityRepository.save(activityWithStatus(ActivityReviewStatus.approved, ActivityRuntimeStatus.takenDown));
        activityRepository.save(activityWithStatus(ActivityReviewStatus.rejected, ActivityRuntimeStatus.ongoing));

        PageResult<ActivityDtos.ActivitySummary> result =
                activityFeedService.getFeed("latest", criteriaWithPage(1, 20));

        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("默认 Tab 使用推荐模式")
    void shouldDefaultToRecommendedTab() {
        activityRepository.save(activity("默认测试", Instant.now()));

        PageResult<ActivityDtos.ActivitySummary> result = activityFeedService.getFeed(null, criteriaWithPage(1, 20));

        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("空结果返回正确元数据")
    void shouldReturnEmptyResultWithCorrectMetadata() {
        PageResult<ActivityDtos.ActivitySummary> result =
                activityFeedService.getFeed("latest", criteriaWithPage(1, 20));

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("computeBoundingBox 在赤道附近计算正确范围")
    void shouldComputeCorrectBoundingBoxAtEquator() {
        double[] bbox = ActivitySearchService.computeBoundingBox(0.0, 0.0, 111_320);
        // 赤道上 1 度经度 ≈ 111320m，所以半径 111320m 的 bbox 约 ±1 度
        assertThat(bbox[0]).isCloseTo(-1.0, within(0.01)); // minLat
        assertThat(bbox[1]).isCloseTo(1.0, within(0.01)); // maxLat
        assertThat(bbox[2]).isCloseTo(-1.0, within(0.01)); // minLon
        assertThat(bbox[3]).isCloseTo(1.0, within(0.01)); // maxLon
    }

    @Test
    @DisplayName("computeBoundingBox 在高纬度地区经度范围更大")
    void shouldComputeWiderLongitudeAtHighLatitude() {
        double[] bboxEquator = ActivitySearchService.computeBoundingBox(0.0, 0.0, 100_000);
        double[] bboxHigh = ActivitySearchService.computeBoundingBox(60.0, 0.0, 100_000);
        double lonWidthEquator = bboxEquator[3] - bboxEquator[2];
        double lonWidthHigh = bboxHigh[3] - bboxHigh[2];
        // 高纬度经度跨度应更大（cos(60°) = 0.5，经度跨度约为赤道的 2 倍）
        assertThat(lonWidthHigh).isGreaterThan(lonWidthEquator * 1.5);
    }

    @Test
    @DisplayName("附近 Tab 使用 bbox 预过滤排除远处活动")
    void shouldExcludeDistantActivitiesByBboxForNearbyTab() {
        Instant now = Instant.now();
        // 天安门附近
        activityRepository.save(activityWithLocation("故宫", 39.9180, 116.3975, now));
        // 上海（远在天安门 bbox 之外）
        activityRepository.save(activityWithLocation("东方明珠", 31.2304, 121.4737, now));

        // 以天安门为中心，半径 10km
        PageResult<ActivityDtos.ActivitySummary> result =
                activityFeedService.getFeed("nearby", criteriaWithLocation(39.9042, 116.3975, 10000, 1, 20));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("故宫");
    }

    @Test
    @DisplayName("推荐 Tab 对大数量活动仍能正确分页")
    void shouldPaginateLargeRecommendedFeedCorrectly() {
        Instant now = Instant.now();
        for (int i = 0; i < 50; i++) {
            activityRepository.save(activity("活动" + i, now.minusSeconds(i * 60)));
        }

        PageResult<ActivityDtos.ActivitySummary> page1 =
                activityFeedService.getFeed("recommended", criteriaWithPage(1, 10));

        assertThat(page1.getItems()).hasSize(10);
        assertThat(page1.getTotal()).isEqualTo(50);
        assertThat(page1.getTotalPages()).isEqualTo(5);
        // 单页内不应有重复活动
        List<String> ids = page1.getItems().stream()
                .map(ActivityDtos.ActivitySummary::getActivityId)
                .toList();
        assertThat(ids).doesNotHaveDuplicates();
    }
}

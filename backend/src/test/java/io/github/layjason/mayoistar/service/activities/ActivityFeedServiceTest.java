package io.github.layjason.mayoistar.service.activities;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.github.layjason.mayoistar.service.ai.AiContentReviewSnapshotMapper;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.time.Instant;
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
@Import({ActivityFeedService.class, ActivityDtoMapper.class, ActivityFeedServiceTest.TestConfig.class})
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

    @Test
    @DisplayName("最新 Tab 按创建时间降序排列")
    void shouldSortByCreatedAtDescForLatestTab() {
        Instant now = Instant.now();
        activityRepository.save(activity("旧活动", now.minusSeconds(7200)));
        activityRepository.save(activity("新活动", now));

        PageResult<ActivityDtos.ActivitySummary> result = activityFeedService.getFeed("latest", 1, 20, null, null);

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

        PageResult<ActivityDtos.ActivitySummary> page1 = activityFeedService.getFeed("recommended", 1, 10, null, null);
        PageResult<ActivityDtos.ActivitySummary> page2 = activityFeedService.getFeed("recommended", 2, 10, null, null);

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
                activityFeedService.getFeed("nearby", 1, 20, 39.9042, 116.3975);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("故宫");
        assertThat(result.getItems().get(1).getTitle()).isEqualTo("鸟巢");
    }

    @Test
    @DisplayName("附近 Tab 无坐标时回退按最新排序")
    void shouldFallbackToLatestWhenNearbyWithoutCoordinates() {
        Instant now = Instant.now();
        activityRepository.save(activity("旧活动", now.minusSeconds(3600)));
        activityRepository.save(activity("新活动", now));

        PageResult<ActivityDtos.ActivitySummary> result = activityFeedService.getFeed("nearby", 1, 20, null, null);

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

        PageResult<ActivityDtos.ActivitySummary> result = activityFeedService.getFeed("latest", 1, 20, null, null);

        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("默认 Tab 使用推荐模式")
    void shouldDefaultToRecommendedTab() {
        activityRepository.save(activity("默认测试", Instant.now()));

        PageResult<ActivityDtos.ActivitySummary> result = activityFeedService.getFeed(null, 1, 20, null, null);

        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("空结果返回正确元数据")
    void shouldReturnEmptyResultWithCorrectMetadata() {
        PageResult<ActivityDtos.ActivitySummary> result = activityFeedService.getFeed("latest", 1, 20, null, null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getTotalPages()).isZero();
    }
}

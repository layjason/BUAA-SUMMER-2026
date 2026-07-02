package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.config.TestSecurityConfiguration;
import io.github.layjason.mayoistar.config.TestStorageConfiguration;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ActivityReviewRecordRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.service.activities.ActivityQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestSecurityConfiguration.class, TestStorageConfiguration.class})
class ActivityQueryServiceTests {

    @Autowired
    private ActivityQueryService activityQueryService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityImageRepository activityImageRepository;

    @Autowired
    private ActivityReviewRecordRepository activityReviewRecordRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        activityReviewRecordRepository.deleteAll();
        activityImageRepository.deleteAll();
        activityRepository.deleteAll();
        mediaFileRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getActivityShouldReturnDetailForApprovedActivity() {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "公开活动");

        ActivityDtos.ActivityDetail detail =
                activityQueryService.getActivity(Optional.empty(), activity.getActivityId());

        assertThat(detail.getActivityId()).isEqualTo(activity.getActivityId());
        assertThat(detail.getTitle()).isEqualTo("公开活动");
        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.approved);
        assertThat(detail.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);
        assertThat(detail.getOrganizerName()).isEqualTo(organizer.getNickname());
        assertThat(detail.getRegisteredCount()).isZero();
        assertThat(detail.getWaitingCount()).isZero();
    }

    @Test
    void getActivityShouldReturnDetailForOwnDraft() {
        User organizer = saveUser("user-a");
        Activity activity = saveDraftActivity(organizer.getUserId(), "我的草稿");

        ActivityDtos.ActivityDetail detail =
                activityQueryService.getActivity(Optional.of(organizer.getUserId()), activity.getActivityId());

        assertThat(detail.getActivityId()).isEqualTo(activity.getActivityId());
        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.draft);
    }

    @Test
    void getActivityShouldRejectNonOwnerForDraft() {
        User organizer = saveUser("user-a");
        saveUser("user-b");
        Activity activity = saveDraftActivity(organizer.getUserId(), "草稿");

        assertThatThrownBy(() -> activityQueryService.getActivity(Optional.of("user-b"), activity.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not visible");
    }

    @Test
    void getActivityShouldRejectAnonymousForDraft() {
        User organizer = saveUser("user-a");
        Activity activity = saveDraftActivity(organizer.getUserId(), "草稿");

        assertThatThrownBy(() -> activityQueryService.getActivity(Optional.empty(), activity.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not visible");
    }

    @Test
    void getActivityShouldRejectNonOwnerForTakenDown() {
        User organizer = saveUser("user-a");
        saveUser("user-b");
        Activity activity = saveTakenDownActivity(organizer.getUserId());

        assertThatThrownBy(() -> activityQueryService.getActivity(Optional.of("user-b"), activity.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not visible");
    }

    @Test
    void getActivityShouldReturnDetailForOwnTakenDown() {
        User organizer = saveUser("user-a");
        Activity activity = saveTakenDownActivity(organizer.getUserId());

        ActivityDtos.ActivityDetail detail =
                activityQueryService.getActivity(Optional.of(organizer.getUserId()), activity.getActivityId());

        assertThat(detail.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.takenDown);
    }

    @Test
    void getActivityShouldReturnDetailForApprovedWhenAuthenticated() {
        User organizer = saveUser("user-a");
        saveUser("user-b");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "公开活动");

        // user-b 也能看到 approved 的活动
        ActivityDtos.ActivityDetail detail =
                activityQueryService.getActivity(Optional.of("user-b"), activity.getActivityId());

        assertThat(detail.getTitle()).isEqualTo("公开活动");
    }

    @Test
    void getActivityShouldThrowWhenNotFound() {
        assertThatThrownBy(() -> activityQueryService.getActivity(Optional.empty(), "non-existent"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not visible");
    }

    @Test
    void getActivityShouldMapReviewStatusCorrectly() {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "测试活动");

        ActivityDtos.ActivityDetail detail =
                activityQueryService.getActivity(Optional.empty(), activity.getActivityId());

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.approved);
        assertThat(detail.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);
    }

    @Test
    void listMyActivitiesShouldOnlyReturnCurrentUsersActivities() {
        User userA = saveUser("user-a");
        User userB = saveUser("user-b");
        saveApprovedActivity(userA.getUserId(), "A的活动1");
        saveApprovedActivity(userA.getUserId(), "A的活动2");
        saveApprovedActivity(userB.getUserId(), "B的活动");

        PageResult<ActivityDtos.ActivitySummary> result =
                activityQueryService.listMyActivities(userA.getUserId(), null, 1, 20);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).allMatch(item -> item.getTitle().contains("A的活动"));
    }

    @Test
    void listMyActivitiesShouldFilterByStatus() {
        User user = saveUser("user-a");
        saveApprovedActivity(user.getUserId(), "已通过");
        saveDraftActivity(user.getUserId(), "草稿");

        PageResult<ActivityDtos.ActivitySummary> result =
                activityQueryService.listMyActivities(user.getUserId(), "draft", 1, 20);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getReviewStatus()).isEqualTo(ActivityReviewStatus.draft);
    }

    @Test
    void listMyActivitiesShouldDefaultPageParams() {
        User user = saveUser("user-a");

        PageResult<ActivityDtos.ActivitySummary> result =
                activityQueryService.listMyActivities(user.getUserId(), null, null, null);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
    }

    @Test
    void listMyActivitiesShouldReturnSummaryFields() {
        User user = saveUser("user-a");
        Activity activity = saveApprovedActivity(user.getUserId(), "摘要测试");

        PageResult<ActivityDtos.ActivitySummary> result =
                activityQueryService.listMyActivities(user.getUserId(), null, 1, 20);

        assertThat(result.getItems()).hasSize(1);
        ActivityDtos.ActivitySummary summary = result.getItems().getFirst();
        assertThat(summary.getActivityId()).isEqualTo(activity.getActivityId());
        assertThat(summary.getTitle()).isEqualTo("摘要测试");
        assertThat(summary.getTags()).containsExactly("社交", "桌游");
        assertThat(summary.getReviewStatus()).isEqualTo(ActivityReviewStatus.approved);
        assertThat(summary.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);
        assertThat(summary.getRegisteredCount()).isZero();
        assertThat(summary.getCapacity()).isEqualTo(8);
    }

    @Test
    void listMyActivitiesShouldThrowForInvalidStatus() {
        User user = saveUser("user-a");

        assertThatThrownBy(() -> activityQueryService.listMyActivities(user.getUserId(), "invalid_status", 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的审核状态筛选条件");
    }

    // ========== getMapPoints ==========

    @Test
    void getMapPointsShouldReturnApprovedActivitiesWithCoordinates() {
        User organizer = saveUser("user-a");
        saveApprovedActivity(organizer.getUserId(), "北京活动");
        saveApprovedActivity(organizer.getUserId(), "上海活动");

        List<ActivityDtos.ActivityMapPoint> result =
                activityQueryService.getMapPoints(null, null, null, null, null, null, null, null, null, 1, 20);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(point -> point.getPoint() != null);
        assertThat(result).allMatch(point -> point.getPoint().getLatitude() != null);
        assertThat(result).allMatch(point -> point.getPoint().getLongitude() != null);
    }

    @Test
    void getMapPointsShouldExcludeDrafts() {
        User organizer = saveUser("user-a");
        saveDraftActivity(organizer.getUserId(), "草稿");
        saveApprovedActivity(organizer.getUserId(), "已通过");

        List<ActivityDtos.ActivityMapPoint> result =
                activityQueryService.getMapPoints(null, null, null, null, null, null, null, null, null, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("已通过");
    }

    @Test
    void getMapPointsShouldExcludeTakenDown() {
        User organizer = saveUser("user-a");
        saveTakenDownActivity(organizer.getUserId());
        saveApprovedActivity(organizer.getUserId(), "正常活动");

        List<ActivityDtos.ActivityMapPoint> result =
                activityQueryService.getMapPoints(null, null, null, null, null, null, null, null, null, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("正常活动");
    }

    @Test
    void getMapPointsShouldFilterByKeyword() {
        User organizer = saveUser("user-a");
        saveApprovedActivity(organizer.getUserId(), "桌游之夜");
        saveApprovedActivity(organizer.getUserId(), "羽毛球赛");

        List<ActivityDtos.ActivityMapPoint> result =
                activityQueryService.getMapPoints("桌游", null, null, null, null, null, null, null, null, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("桌游之夜");
    }

    @Test
    void getMapPointsShouldFilterByCity() {
        User organizer = saveUser("user-a");
        saveApprovedActivity(organizer.getUserId(), "北京活动");
        activityRepository.save(Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizer.getUserId())
                .title("上海活动")
                .tags(List.of("社交"))
                .introduction("简介")
                .startAt(Instant.parse("2026-07-02T10:00:00Z"))
                .endAt(Instant.parse("2026-07-02T12:00:00Z"))
                .pointLon(121.473)
                .pointLat(31.230)
                .city("上海")
                .address("浦东新区")
                .safetyNotice("注意安全")
                .capacity(8)
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        List<ActivityDtos.ActivityMapPoint> result =
                activityQueryService.getMapPoints(null, "上海", null, null, null, null, null, null, null, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("上海活动");
    }

    @Test
    void getMapPointsShouldPaginate() {
        User organizer = saveUser("user-a");
        saveApprovedActivity(organizer.getUserId(), "活动1");
        saveApprovedActivity(organizer.getUserId(), "活动2");
        saveApprovedActivity(organizer.getUserId(), "活动3");

        List<ActivityDtos.ActivityMapPoint> page1 =
                activityQueryService.getMapPoints(null, null, null, null, null, null, null, null, null, 1, 2);

        assertThat(page1).hasSize(2);
    }

    // ========== 辅助方法 ==========

    private User saveUser(String userId) {
        return userRepository.save(User.builder()
                .userId(userId)
                .email(userId + "@example.com")
                .nickname("nickname-" + userId)
                .passwordHash("hashed-password")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private Activity saveDraftActivity(String organizerId, String title) {
        return activityRepository.save(Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title(title)
                .tags(List.of("社交", "桌游"))
                .introduction("简介")
                .startAt(Instant.parse("2026-07-02T10:00:00Z"))
                .endAt(Instant.parse("2026-07-02T12:00:00Z"))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("海淀区某街道")
                .placeName("活动中心")
                .safetyNotice("注意安全")
                .capacity(8)
                .registrationDeadline(Instant.parse("2026-07-01T12:00:00Z"))
                .reviewStatus(ActivityReviewStatus.draft)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private Activity saveApprovedActivity(String organizerId, String title) {
        return activityRepository.save(Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title(title)
                .tags(List.of("社交", "桌游"))
                .introduction("简介")
                .startAt(Instant.parse("2026-07-02T10:00:00Z"))
                .endAt(Instant.parse("2026-07-02T12:00:00Z"))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("海淀区某街道")
                .placeName("活动中心")
                .safetyNotice("注意安全")
                .capacity(8)
                .registrationDeadline(Instant.parse("2026-07-01T12:00:00Z"))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private Activity saveTakenDownActivity(String organizerId) {
        return activityRepository.save(Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title("已下架活动")
                .tags(List.of("社交"))
                .introduction("简介")
                .startAt(Instant.parse("2026-07-02T10:00:00Z"))
                .endAt(Instant.parse("2026-07-02T12:00:00Z"))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("海淀区某街道")
                .safetyNotice("注意安全")
                .capacity(8)
                .registrationDeadline(Instant.parse("2026-07-01T12:00:00Z"))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.takenDown)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }
}

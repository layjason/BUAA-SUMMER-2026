package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityReviewRecordRepository;
import io.github.layjason.mayoistar.service.activities.AdminActivityService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AdminActivityServiceTests {

    @Autowired
    private AdminActivityService adminActivityService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityImageRepository activityImageRepository;

    @Autowired
    private ActivityReviewRecordRepository activityReviewRecordRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        activityReviewRecordRepository.deleteAll();
        activityImageRepository.deleteAll();
        activityRepository.deleteAll();
        mediaFileRepository.deleteAll();
        userRepository.deleteAll();
    }

    /* ========== getActivityDetail ========== */

    @Test
    void getActivityDetailShouldReturnDetail() {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "活动");

        ActivityDtos.ActivityDetail detail = adminActivityService.getActivityDetail(activity.getActivityId());

        assertThat(detail.getActivityId()).isEqualTo(activity.getActivityId());
        assertThat(detail.getTitle()).isEqualTo("活动");
        assertThat(detail.getOrganizerName()).isEqualTo(organizer.getNickname());
    }

    @Test
    void getActivityDetailShouldRejectNotFound() {
        assertThatThrownBy(() -> adminActivityService.getActivityDetail("non-existent"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60008)
                .hasMessageContaining("does not exist");
    }

    /* ========== reviewActivity ========== */

    @Test
    void reviewActivityShouldApprovePendingActivity() {
        User organizer = saveUser("user-a");
        Activity activity = savePendingActivity(organizer.getUserId(), "待审核");

        ActivityDtos.ActivityDetail detail =
                adminActivityService.reviewActivity(activity.getActivityId(), ReviewStatus.approved, null);

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.approved);
        assertThat(detail.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);

        Activity updated = activityRepository.findById(activity.getActivityId()).orElseThrow();
        assertThat(updated.getReviewStatus()).isEqualTo(ActivityReviewStatus.approved);
        assertThat(updated.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);
    }

    @Test
    void reviewActivityShouldRejectPendingActivity() {
        User organizer = saveUser("user-a");
        Activity activity = savePendingActivity(organizer.getUserId(), "待审核");

        ActivityDtos.ActivityDetail detail =
                adminActivityService.reviewActivity(activity.getActivityId(), ReviewStatus.rejected, "内容违规");

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.rejected);

        Activity updated = activityRepository.findById(activity.getActivityId()).orElseThrow();
        assertThat(updated.getReviewStatus()).isEqualTo(ActivityReviewStatus.rejected);
    }

    @Test
    void reviewActivityShouldRequireChanges() {
        User organizer = saveUser("user-a");
        Activity activity = savePendingActivity(organizer.getUserId(), "待审核");

        ActivityDtos.ActivityDetail detail =
                adminActivityService.reviewActivity(activity.getActivityId(), ReviewStatus.changeRequired, "请修改简介");

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.changeRequired);

        Activity updated = activityRepository.findById(activity.getActivityId()).orElseThrow();
        assertThat(updated.getReviewStatus()).isEqualTo(ActivityReviewStatus.changeRequired);
    }

    @Test
    void reviewActivityShouldRejectNotFound() {
        assertThatThrownBy(() -> adminActivityService.reviewActivity("non-existent", ReviewStatus.approved, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60008);
    }

    @Test
    void reviewActivityShouldRejectNonPendingStatus() {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "已通过");

        assertThatThrownBy(() ->
                        adminActivityService.reviewActivity(activity.getActivityId(), ReviewStatus.approved, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60009)
                .hasMessageContaining("does not allow this operation");
    }

    @Test
    void reviewActivityShouldRequireReasonForRejection() {
        User organizer = saveUser("user-a");
        Activity activity = savePendingActivity(organizer.getUserId(), "待审核");

        assertThatThrownBy(() ->
                        adminActivityService.reviewActivity(activity.getActivityId(), ReviewStatus.rejected, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60006);
    }

    @Test
    void reviewActivityShouldRequireReasonForChangeRequired() {
        User organizer = saveUser("user-a");
        Activity activity = savePendingActivity(organizer.getUserId(), "待审核");

        assertThatThrownBy(() -> adminActivityService.reviewActivity(
                        activity.getActivityId(), ReviewStatus.changeRequired, "  "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60006);
    }

    @Test
    void reviewActivityShouldCreateReviewRecord() {
        User organizer = saveUser("user-a");
        Activity activity = savePendingActivity(organizer.getUserId(), "待审核");

        adminActivityService.reviewActivity(activity.getActivityId(), ReviewStatus.approved, "通过");

        List<ActivityReviewRecord> records =
                activityReviewRecordRepository.findByActivityIdOrderByReviewedAtDesc(activity.getActivityId());
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getResult()).isEqualTo(ReviewStatus.approved);
        assertThat(records.getFirst().getReason()).isEqualTo("通过");
    }

    /* ========== takeDownActivity ========== */

    @Test
    void takeDownActivityShouldSetRuntimeStatusTakenDown() {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "已发布");

        ActivityDtos.ActivityDetail detail = adminActivityService.takeDownActivity(activity.getActivityId(), "违规内容");

        assertThat(detail.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.takenDown);

        Activity updated = activityRepository.findById(activity.getActivityId()).orElseThrow();
        assertThat(updated.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.takenDown);
    }

    @Test
    void takeDownActivityShouldRejectNotFound() {
        assertThatThrownBy(() -> adminActivityService.takeDownActivity("non-existent", "原因"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60008);
    }

    @Test
    void takeDownActivityShouldRejectAlreadyTakenDown() {
        User organizer = saveUser("user-a");
        Activity activity = saveTakenDownActivity(organizer.getUserId());

        assertThatThrownBy(() -> adminActivityService.takeDownActivity(activity.getActivityId(), "再次下架"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60009);
    }

    @Test
    void takeDownActivityShouldRequireReason() {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "已发布");

        assertThatThrownBy(() -> adminActivityService.takeDownActivity(activity.getActivityId(), "  "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60006);
    }

    @Test
    void takeDownActivityShouldCreateReviewRecord() {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "已发布");

        adminActivityService.takeDownActivity(activity.getActivityId(), "违规内容");

        List<ActivityReviewRecord> records =
                activityReviewRecordRepository.findByActivityIdOrderByReviewedAtDesc(activity.getActivityId());
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getResult()).isEqualTo(ReviewStatus.rejected);
        assertThat(records.getFirst().getReason()).isEqualTo("违规内容");
    }

    @Test
    void takeDownShouldRejectDraftActivity() {
        User organizer = saveUser("user-a");
        Activity activity = saveDraftActivity(organizer.getUserId(), "草稿活动");

        assertThatThrownBy(() -> adminActivityService.takeDownActivity(activity.getActivityId(), "下架草稿"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60010);
    }

    /* ========== restoreActivity ========== */

    @Test
    void restoreActivityShouldRestoreRuntimeStatus() {
        User organizer = saveUser("user-a");
        Activity activity = saveTakenDownActivity(organizer.getUserId());

        ActivityDtos.ActivityDetail detail = adminActivityService.restoreActivity(activity.getActivityId());

        assertThat(detail.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);

        Activity updated = activityRepository.findById(activity.getActivityId()).orElseThrow();
        assertThat(updated.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);
    }

    @Test
    void restoreActivityShouldRejectNotFound() {
        assertThatThrownBy(() -> adminActivityService.restoreActivity("non-existent"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60008);
    }

    @Test
    void restoreActivityShouldRejectNotTakenDown() {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "已发布");

        assertThatThrownBy(() -> adminActivityService.restoreActivity(activity.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 60009);
    }

    @Test
    void restoreActivityShouldCreateAuditRecord() {
        User organizer = saveUser("user-a");
        Activity activity = saveTakenDownActivity(organizer.getUserId());

        adminActivityService.restoreActivity(activity.getActivityId());

        List<ActivityReviewRecord> records =
                activityReviewRecordRepository.findByActivityIdOrderByReviewedAtDesc(activity.getActivityId());
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getResult()).isEqualTo(ReviewStatus.approved);
        assertThat(records.getFirst().getReason()).isEqualTo("管理员恢复活动");
    }

    /* ========== 辅助方法 ========== */

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

    private Activity saveApprovedActivity(String organizerId, String title) {
        return activityRepository.save(activityBuilder(organizerId, title)
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .build());
    }

    private Activity savePendingActivity(String organizerId, String title) {
        return activityRepository.save(activityBuilder(organizerId, title)
                .reviewStatus(ActivityReviewStatus.pending)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .build());
    }

    private Activity saveDraftActivity(String organizerId, String title) {
        return activityRepository.save(activityBuilder(organizerId, title)
                .reviewStatus(ActivityReviewStatus.draft)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .build());
    }

    private Activity saveTakenDownActivity(String organizerId) {
        return activityRepository.save(activityBuilder(organizerId, "已下架")
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.takenDown)
                .build());
    }

    private Activity.ActivityBuilder activityBuilder(String organizerId, String title) {
        return Activity.builder()
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
                .createdAt(Instant.now())
                .updatedAt(Instant.now());
    }
}

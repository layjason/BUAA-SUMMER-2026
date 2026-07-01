package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
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
import io.github.layjason.mayoistar.service.activities.ActivityDraftService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ActivityDraftServiceTests {

    @Autowired
    private ActivityDraftService activityDraftService;

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
    void saveDraftShouldPersistDraftAndImages() {
        User organizer = saveUser("user-a");
        MediaFile image = saveMediaFile("image-a", organizer.getUserId());

        ActivityDtos.ActivityDraftUpsertRequest request = createDraftRequest(List.of(image.getMediaId()));
        ActivityDtos.ActivityDraftDetail draftDetail = activityDraftService.saveDraft(organizer.getUserId(), request);

        Activity savedActivity =
                activityRepository.findById(draftDetail.getActivityId()).orElseThrow();
        assertThat(savedActivity.getOrganizerId()).isEqualTo(organizer.getUserId());
        assertThat(savedActivity.getReviewStatus()).isEqualTo(ActivityReviewStatus.draft);
        assertThat(savedActivity.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);
        assertThat(draftDetail.getImages()).hasSize(1);
        assertThat(draftDetail.getImages().getFirst().getMediaId()).isEqualTo(image.getMediaId());
    }

    @Test
    void updateDraftShouldRejectNonOwner() {
        User organizer = saveUser("user-a");
        saveUser("user-b");
        ActivityDtos.ActivityDraftDetail created =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));

        assertThatThrownBy(() -> activityDraftService.updateDraft(
                        "user-b", created.getActivityId(), createDraftRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问其他用户的活动草稿");
    }

    @Test
    void saveDraftShouldRejectInvalidSchedule() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftUpsertRequest request = createDraftRequest(List.of());
        request.setStartAt("2026-07-02T10:00:00Z");
        request.setEndAt("2026-07-02T09:00:00Z");

        assertThatThrownBy(() -> activityDraftService.saveDraft(organizer.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动结束时间必须晚于开始时间");
    }

    @Test
    void listDraftsShouldOnlyReturnCurrentUsersDrafts() {
        User organizer = saveUser("user-a");
        User otherOrganizer = saveUser("user-b");
        activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        activityDraftService.saveDraft(otherOrganizer.getUserId(), createDraftRequest(List.of()));

        assertThat(activityDraftService.listDrafts(organizer.getUserId(), 1, 20).getItems())
                .hasSize(1);
    }

    @Test
    void submitActivityShouldTransitionFromDraftToPending() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));

        ActivityDtos.ActivityDetail detail =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.pending);
        assertThat(detail.getOrganizerName()).isEqualTo(organizer.getNickname());
        assertThat(detail.getReviewRecords()).hasSize(1);
        assertThat(detail.getReviewRecords().getFirst().getResult())
                .isEqualTo(io.github.layjason.mayoistar.entity.common.ReviewStatus.pending);

        Activity savedActivity =
                activityRepository.findById(draft.getActivityId()).orElseThrow();
        assertThat(savedActivity.getReviewStatus()).isEqualTo(ActivityReviewStatus.pending);
        assertThat(savedActivity.getManualReviewRequired()).isFalse();
    }

    @Test
    void submitActivityShouldTriggerManualReviewForLargeCapacity() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftUpsertRequest request = createDraftRequest(List.of());
        request.setCapacity(60);
        ActivityDtos.ActivityDraftDetail draft = activityDraftService.saveDraft(organizer.getUserId(), request);

        ActivityDtos.ActivityDetail detail =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThat(detail.getManualReviewRequired()).isTrue();
        Activity savedActivity =
                activityRepository.findById(draft.getActivityId()).orElseThrow();
        assertThat(savedActivity.getManualReviewRequired()).isTrue();
    }

    @Test
    void submitActivityShouldRejectNonOwner() {
        User organizer = saveUser("user-a");
        saveUser("user-b");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));

        assertThatThrownBy(() -> activityDraftService.submitActivity("user-b", draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作其他用户的活动");
    }

    @Test
    void submitActivityShouldRejectWhenNotFound() {
        saveUser("user-a");

        assertThatThrownBy(() -> activityDraftService.submitActivity("user-a", "non-existent-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not visible");
    }

    @Test
    void submitActivityShouldRejectWhenAlreadyPending() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已在审核中");
    }

    @Test
    void submitActivityShouldRejectWhenRejected() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setReviewStatus(ActivityReviewStatus.rejected);
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被驳回");
    }

    @Test
    void submitActivityShouldRejectWhenMissingTitle() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        // 将标题设为空白，模拟校验边界情况（DB 不允许 NULL，但允许空白字符串）
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setTitle("  ");
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动名称不能为空");
    }

    @Test
    void submitActivityShouldRejectWhenMissingTags() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setTags(null);
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动标签不能为空");
    }

    @Test
    void submitActivityShouldRejectWhenMissingLocation() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setPointLon(null);
        activity.setPointLat(null);
        activity.setCity(null);
        activity.setAddress(null);
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动地点信息不完整");
    }

    @Test
    void submitActivityShouldRejectWhenInvalidSchedule() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        // 直接修改实体将结束时间设为早于开始时间，模拟边界情况
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setEndAt(Instant.parse("2026-07-01T10:00:00Z")); // 早于 startAt 2026-07-02T10:00:00Z
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动结束时间必须晚于开始时间");
    }

    @Test
    void submitActivityShouldAllowResubmitFromChangeRequired() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setReviewStatus(ActivityReviewStatus.changeRequired);
        activityRepository.save(activity);

        ActivityDtos.ActivityDetail detail =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.pending);
    }

    @Test
    void getDraftShouldAllowChangeRequiredStatus() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setReviewStatus(ActivityReviewStatus.changeRequired);
        activityRepository.save(activity);

        ActivityDtos.ActivityDraftDetail result =
                activityDraftService.getDraft(organizer.getUserId(), draft.getActivityId());

        assertThat(result.getActivityId()).isEqualTo(draft.getActivityId());
        assertThat(result.getReviewStatus()).isEqualTo(ActivityReviewStatus.changeRequired);
    }

    @Test
    void updateDraftShouldAllowChangeRequiredStatus() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setReviewStatus(ActivityReviewStatus.changeRequired);
        activityRepository.save(activity);

        ActivityDtos.ActivityDraftUpsertRequest request = createDraftRequest(List.of());
        request.setTitle("修改后标题");
        ActivityDtos.ActivityDraftDetail result =
                activityDraftService.updateDraft(organizer.getUserId(), draft.getActivityId(), request);

        assertThat(result.getTitle()).isEqualTo("修改后标题");
    }

    @Test
    void listDraftsShouldIncludeChangeRequiredActivities() {
        User organizer = saveUser("user-a");
        // 创建一条 draft 状态的草稿
        activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        // 创建第二条草稿并设为 changeRequired
        ActivityDtos.ActivityDraftDetail draft2 =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity2 = activityRepository.findById(draft2.getActivityId()).orElseThrow();
        activity2.setReviewStatus(ActivityReviewStatus.changeRequired);
        activityRepository.save(activity2);

        assertThat(activityDraftService.listDrafts(organizer.getUserId(), 1, 20).getItems())
                .hasSize(2);
    }

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

    private MediaFile saveMediaFile(String mediaId, String userId) {
        return mediaFileRepository.save(MediaFile.builder()
                .mediaId(mediaId)
                .fileName("cover.png")
                .contentType("image/png")
                .sizeBytes(1L)
                .usage(MediaUsage.activityImage)
                .storagePath("/tmp/" + mediaId)
                .url("https://example.com/" + mediaId)
                .uploadedBy(userId)
                .uploadedAt(Instant.now())
                .build());
    }

    private ActivityDtos.ActivityDraftUpsertRequest createDraftRequest(List<String> imageIds) {
        ActivityDtos.ActivityDraftUpsertRequest request = new ActivityDtos.ActivityDraftUpsertRequest();
        request.setTitle("桌游局");
        request.setTags(List.of("社交", "桌游"));
        request.setIntroduction("一起玩桌游");
        request.setStartAt("2026-07-02T10:00:00Z");
        request.setEndAt("2026-07-02T12:00:00Z");
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(116.397);
        point.setLatitude(39.907);
        CommonDtos.LocationInfo location = new CommonDtos.LocationInfo();
        location.setPoint(point);
        location.setCity("北京");
        location.setAddress("海淀区某街道");
        location.setPlaceName("活动中心");
        request.setLocation(location);
        request.setSafetyNotice("注意人身安全");
        request.setCapacity(8);
        request.setRegistrationDeadline("2026-07-01T12:00:00Z");
        request.setFeeDescription("AA");
        request.setMinAge(18);
        request.setImageIds(imageIds);
        return request;
    }
}

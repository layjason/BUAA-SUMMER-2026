package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.BusinessException;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRepository;
import io.github.layjason.mayoistar.repository.common.MediaFileRepository;
import io.github.layjason.mayoistar.repository.identity.UserRepository;
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
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        activityImageRepository.deleteAll();
        activityRepository.deleteAll();
        mediaFileRepository.deleteAll();
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

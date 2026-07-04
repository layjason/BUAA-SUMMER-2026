package io.github.layjason.mayoistar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.config.TestSecurityConfiguration;
import io.github.layjason.mayoistar.config.TestStorageConfiguration;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityReviewRepository;
import io.github.layjason.mayoistar.repository.activities.ActivitySummaryImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivitySummaryPostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestSecurityConfiguration.class, TestStorageConfiguration.class})
class ActivitySummaryReviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityRegistrationRepository activityRegistrationRepository;

    @Autowired
    private ActivitySummaryPostRepository activitySummaryPostRepository;

    @Autowired
    private ActivitySummaryImageRepository activitySummaryImageRepository;

    @Autowired
    private ActivityReviewRepository activityReviewRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        activitySummaryImageRepository.deleteAll();
        activitySummaryPostRepository.deleteAll();
        activityReviewRepository.deleteAll();
        activityRegistrationRepository.deleteAll();
        activityRepository.deleteAll();
        mediaFileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createSummaryShouldPersistSummaryForOrganizer() throws Exception {
        saveUser("organizer");
        Activity activity = saveEndedActivity("organizer");
        UUID mediaId = UUID.randomUUID();
        saveMediaFile(mediaId, "organizer", MediaUsage.activityImage);

        mockMvc.perform(post("/activities/{activityId}/summaries", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("organizer")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(summaryRequest(mediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").value(activity.getActivityId()))
                .andExpect(jsonPath("$.data.title").value("复盘标题"))
                .andExpect(jsonPath("$.data.images[0].mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.data.imageTags[0].tags[0]").value("合影"));
    }

    @Test
    void listSummariesAndGetMySummaryShouldReturnCreatedSummary() throws Exception {
        saveUser("organizer");
        Activity activity = saveEndedActivity("organizer");
        UUID mediaId = UUID.randomUUID();
        saveMediaFile(mediaId, "organizer", MediaUsage.activityImage);

        mockMvc.perform(post("/activities/{activityId}/summaries", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("organizer")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(summaryRequest(mediaId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/activities/{activityId}/summaries", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("复盘标题"))
                .andExpect(jsonPath("$.data.items[0].images[0].mediaId").value(mediaId.toString()));

        mockMvc.perform(get("/activities/{activityId}/summaries/mine", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("organizer")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.title").value("复盘标题"));
    }

    @Test
    void createSummaryShouldRejectDuplicateSummary() throws Exception {
        saveUser("organizer");
        Activity activity = saveEndedActivity("organizer");
        UUID mediaId = UUID.randomUUID();
        saveMediaFile(mediaId, "organizer", MediaUsage.activityImage);

        mockMvc.perform(post("/activities/{activityId}/summaries", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("organizer")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(summaryRequest(mediaId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/activities/{activityId}/summaries", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("organizer")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(summaryRequest(mediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20020))
                .andExpect(jsonPath("$.message").value("Activity summary already exists"));
    }

    @Test
    void createSummaryShouldRejectNonOrganizer() throws Exception {
        saveUser("organizer");
        saveUser("participant");
        Activity activity = saveEndedActivity("organizer");
        UUID mediaId = UUID.randomUUID();
        saveMediaFile(mediaId, "participant", MediaUsage.activityImage);

        mockMvc.perform(post("/activities/{activityId}/summaries", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("participant")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(summaryRequest(mediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    void createSummaryShouldRejectNotEndedActivity() throws Exception {
        saveUser("organizer");
        Activity activity = saveFutureActivity("organizer");
        UUID mediaId = UUID.randomUUID();
        saveMediaFile(mediaId, "organizer", MediaUsage.activityImage);

        mockMvc.perform(post("/activities/{activityId}/summaries", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("organizer")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(summaryRequest(mediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20015))
                .andExpect(jsonPath("$.message").value("Activity has not ended"));
    }

    @Test
    void createReviewShouldPersistReviewForCheckedInParticipant() throws Exception {
        saveUser("organizer");
        saveUser("participant");
        Activity activity = saveEndedActivity("organizer");
        saveRegistration(activity.getActivityId(), "participant", RegistrationStatus.checkedIn);

        mockMvc.perform(post("/activities/{activityId}/reviews", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("participant")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").value(activity.getActivityId()))
                .andExpect(jsonPath("$.data.userId").value("participant"))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.tags[0]").value("准时"));
    }

    @Test
    void listReviewsAndGetMyReviewShouldReturnCreatedReview() throws Exception {
        saveUser("organizer");
        saveUser("participant");
        Activity activity = saveEndedActivity("organizer");
        saveRegistration(activity.getActivityId(), "participant", RegistrationStatus.checkedIn);

        mockMvc.perform(post("/activities/{activityId}/reviews", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("participant")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/activities/{activityId}/reviews", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].rating").value(5))
                .andExpect(jsonPath("$.data.items[0].nickname").value("nickname-participant"));

        mockMvc.perform(get("/activities/{activityId}/reviews/mine", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("participant")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review.rating").value(5));
    }

    @Test
    void getMyReviewShouldReturnEmptyObjectWhenCurrentUserHasNoReview() throws Exception {
        saveUser("organizer");
        saveUser("participant");
        Activity activity = saveEndedActivity("organizer");

        mockMvc.perform(get("/activities/{activityId}/reviews/mine", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("participant")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review").doesNotExist());
    }

    @Test
    void createReviewShouldRejectParticipantWithoutCheckIn() throws Exception {
        saveUser("organizer");
        saveUser("participant");
        Activity activity = saveEndedActivity("organizer");
        saveRegistration(activity.getActivityId(), "participant", RegistrationStatus.registered);

        mockMvc.perform(post("/activities/{activityId}/reviews", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("participant")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20011))
                .andExpect(jsonPath("$.message").value("Registration does not exist"));
    }

    @Test
    void createReviewShouldRejectDuplicateReview() throws Exception {
        saveUser("organizer");
        saveUser("participant");
        Activity activity = saveEndedActivity("organizer");
        saveRegistration(activity.getActivityId(), "participant", RegistrationStatus.checkedIn);

        mockMvc.perform(post("/activities/{activityId}/reviews", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("participant")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/activities/{activityId}/reviews", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("participant")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20016))
                .andExpect(jsonPath("$.message").value("Activity review already exists"));
    }

    private String summaryRequest(UUID mediaId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title",
                "复盘标题",
                "content",
                "这次活动效果很好",
                "imageIds",
                List.of(mediaId),
                "confirmedImageTags",
                List.of(Map.of("mediaId", mediaId, "tags", List.of("合影", "成果展示")))));
    }

    private String reviewRequest() throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("rating", 5, "content", "组织很好，现场体验顺畅", "tags", List.of("准时", "友好")));
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

    private Activity saveEndedActivity(String organizerId) {
        return saveActivity(
                organizerId, ActivityRuntimeStatus.ended, Instant.now().minusSeconds(7200));
    }

    private Activity saveFutureActivity(String organizerId) {
        return saveActivity(
                organizerId, ActivityRuntimeStatus.notStarted, Instant.now().plusSeconds(7200));
    }

    private Activity saveActivity(String organizerId, ActivityRuntimeStatus runtimeStatus, Instant endAt) {
        return activityRepository.save(Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title("复盘活动")
                .tags(List.of("社交"))
                .introduction("简介")
                .startAt(endAt.minusSeconds(3600))
                .endAt(endAt)
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("海淀区某街道")
                .safetyNotice("注意安全")
                .capacity(8)
                .registrationDeadline(endAt.minusSeconds(7200))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(runtimeStatus)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private ActivityRegistration saveRegistration(String activityId, String userId, RegistrationStatus status) {
        return activityRegistrationRepository.save(ActivityRegistration.builder()
                .registrationId(UUID.randomUUID().toString())
                .activityId(activityId)
                .userId(userId)
                .status(status)
                .participantNote("测试备注")
                .acceptedSafetyNotice(true)
                .registeredAt(Instant.now().minusSeconds(3600))
                .checkedInAt(
                        status == RegistrationStatus.checkedIn ? Instant.now().minusSeconds(1800) : null)
                .build());
    }

    private MediaFile saveMediaFile(UUID mediaId, String uploadedBy, MediaUsage usage) {
        return mediaFileRepository.save(MediaFile.builder()
                .mediaId(mediaId)
                .fileName(mediaId + ".png")
                .contentType("image/png")
                .sizeBytes(1024L)
                .usage(usage)
                .storagePath("activityImage/" + uploadedBy + "/" + mediaId + ".png")
                .visibility(MediaVisibility.publicVisible)
                .accessPolicy(MediaAccessPolicy.publicAccess)
                .accessScopeId("")
                .accessVersion(1L)
                .uploadedBy(uploadedBy)
                .uploadedAt(Instant.now())
                .build());
    }
}

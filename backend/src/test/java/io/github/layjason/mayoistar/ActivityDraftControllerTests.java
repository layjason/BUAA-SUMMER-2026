package io.github.layjason.mayoistar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ActivityReviewRecordRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.service.activities.RequestActorResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser("test-user-id")
class ActivityDraftControllerTests {

    @Autowired
    private MockMvc mockMvc;

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
    void saveDraftShouldReturnPersistedDraftWhenHeaderPresent() throws Exception {
        saveUser("user-a");
        saveMediaFile("image-a", "user-a");

        mockMvc.perform(post("/activities/drafts")
                        .header(RequestActorResolver.USER_ID_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of("image-a"), "桌游局")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("桌游局"))
                .andExpect(jsonPath("$.data.images[0].mediaId").value("image-a"));
    }

    @Test
    void listDraftsShouldReturnCurrentUsersDrafts() throws Exception {
        saveUser("user-a");
        saveUser("user-b");
        mockMvc.perform(post("/activities/drafts")
                        .header(RequestActorResolver.USER_ID_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "桌游局")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/activities/drafts")
                        .header(RequestActorResolver.USER_ID_HEADER, "user-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "羽毛球局")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/activities/drafts").header(RequestActorResolver.USER_ID_HEADER, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void updateDraftShouldRefreshTitle() throws Exception {
        saveUser("user-a");
        String responseBody = mockMvc.perform(post("/activities/drafts")
                        .header(RequestActorResolver.USER_ID_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "桌游局")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String activityId = extractActivityId(responseBody);

        mockMvc.perform(patch("/activities/drafts/{activityId}", activityId)
                        .header(RequestActorResolver.USER_ID_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "更新后的活动标题")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("更新后的活动标题"));
    }

    @Test
    void saveDraftShouldFallbackToContractPlaceholderWhenHeaderMissing() throws Exception {
        mockMvc.perform(post("/activities/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "桌游局")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").value("activity-placeholder"));
    }

    @Test
    void submitActivityShouldReturnDetailWhenSuccessful() throws Exception {
        saveUser("user-a");
        String responseBody = mockMvc.perform(post("/activities/drafts")
                        .header(RequestActorResolver.USER_ID_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "提交测试活动")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String activityId = extractActivityId(responseBody);

        mockMvc.perform(post("/activities/{activityId}/submit", activityId)
                        .header(RequestActorResolver.USER_ID_HEADER, "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").value(activityId))
                .andExpect(jsonPath("$.data.reviewStatus").value("pending"))
                .andExpect(jsonPath("$.data.organizerName").value("nickname-user-a"))
                .andExpect(jsonPath("$.data.reviewRecords.length()").value(1));
    }

    @Test
    void submitActivityShouldRejectWhenNotOwner() throws Exception {
        saveUser("user-a");
        saveUser("user-b");
        String responseBody = mockMvc.perform(post("/activities/drafts")
                        .header(RequestActorResolver.USER_ID_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "桌游局")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String activityId = extractActivityId(responseBody);

        mockMvc.perform(post("/activities/{activityId}/submit", activityId)
                        .header(RequestActorResolver.USER_ID_HEADER, "user-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    void submitActivityShouldFallbackWhenHeaderMissing() throws Exception {
        mockMvc.perform(post("/activities/{activityId}/submit", "any-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").value("activity-placeholder"));
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

    private String createDraftRequestJson(List<String> imageIds, String title) {
        String imageIdsJson = imageIds.stream()
                .map(imageId -> "\"" + imageId + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "{"
                + "\"title\":\"" + title + "\","
                + "\"tags\":[\"社交\",\"桌游\"],"
                + "\"introduction\":\"一起玩桌游\","
                + "\"startAt\":\"2026-07-02T10:00:00Z\","
                + "\"endAt\":\"2026-07-02T12:00:00Z\","
                + "\"location\":{\"point\":{\"longitude\":116.397,\"latitude\":39.907},"
                + "\"city\":\"北京\",\"address\":\"海淀区某街道\",\"placeName\":\"活动中心\"},"
                + "\"safetyNotice\":\"注意人身安全\","
                + "\"capacity\":8,"
                + "\"registrationDeadline\":\"2026-07-01T12:00:00Z\","
                + "\"feeDescription\":\"AA\","
                + "\"minAge\":18,"
                + "\"imageIds\":[" + imageIdsJson + "]"
                + "}";
    }

    private String extractActivityId(String responseBody) {
        String marker = "\"activityId\":\"";
        int valueStartIndex = responseBody.indexOf(marker) + marker.length();
        int valueEndIndex = responseBody.indexOf('"', valueStartIndex);
        return responseBody.substring(valueStartIndex, valueEndIndex);
    }
}

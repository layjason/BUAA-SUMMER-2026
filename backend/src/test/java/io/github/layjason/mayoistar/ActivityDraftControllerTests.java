package io.github.layjason.mayoistar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.layjason.mayoistar.config.TestSecurityConfiguration;
import io.github.layjason.mayoistar.config.TestStorageConfiguration;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityTemplate;
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
import io.github.layjason.mayoistar.repository.activities.ActivityTemplateRepository;
import java.time.Instant;
import java.util.List;
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
class ActivityDraftControllerTests {

    private static final UUID IMAGE_A_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityImageRepository activityImageRepository;

    @Autowired
    private ActivityTemplateRepository activityTemplateRepository;

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
        activityTemplateRepository.deleteAll();
        mediaFileRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saveDraftShouldReturnPersistedDraftWhenHeaderPresent() throws Exception {
        saveUser("user-a");
        saveMediaFile(IMAGE_A_ID, "user-a");

        mockMvc.perform(post("/activities/drafts")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(IMAGE_A_ID), "桌游局")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("桌游局"))
                .andExpect(jsonPath("$.data.images[0].mediaId").value(IMAGE_A_ID.toString()));
    }

    @Test
    void saveDraftShouldAcceptPartialRequest() throws Exception {
        saveUser("user-a");

        mockMvc.perform(post("/activities/drafts")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"未完成的活动草稿\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("未完成的活动草稿"))
                .andExpect(jsonPath("$.data.startAt").doesNotExist())
                .andExpect(jsonPath("$.data.endAt").doesNotExist())
                .andExpect(jsonPath("$.data.capacity").doesNotExist());
    }

    @Test
    void listDraftsShouldReturnCurrentUsersDrafts() throws Exception {
        saveUser("user-a");
        saveUser("user-b");
        mockMvc.perform(post("/activities/drafts")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "桌游局")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/activities/drafts")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-b")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "羽毛球局")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/activities/drafts")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void listTemplatesShouldReturnPersistedTemplates() throws Exception {
        saveTemplate("template-a", "桌游模板", "社交", List.of("桌游", "轻松"));

        mockMvc.perform(get("/activities/templates")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].templateId").value("template-a"))
                .andExpect(jsonPath("$.data.items[0].activityType").value("社交"))
                .andExpect(jsonPath("$.data.items[0].defaultTags[0]").value("桌游"));
    }

    @Test
    void adminCreateActivityTemplateShouldPersistTemplate() throws Exception {
        mockMvc.perform(post("/admin/activities/templates")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin-a")
                                .roles("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTemplateRequestJson("城市探索模板", "城市探索", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateId").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("城市探索模板"))
                .andExpect(jsonPath("$.data.activityType").value("城市探索"))
                .andExpect(jsonPath("$.data.defaultTags[0]").value("探索"));

        mockMvc.perform(get("/activities/templates")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("城市探索模板"));
    }

    @Test
    void adminUpdateActivityTemplateShouldOverwriteTemplate() throws Exception {
        saveTemplate("template-a", "桌游模板", "社交", List.of("桌游", "轻松"));

        mockMvc.perform(put("/admin/activities/templates/{templateId}", "template-a")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin-a")
                                .roles("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTemplateRequestJson("更新模板", "户外", 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateId").value("template-a"))
                .andExpect(jsonPath("$.data.name").value("更新模板"))
                .andExpect(jsonPath("$.data.defaultCapacity").value(30));
    }

    @Test
    void adminDeleteActivityTemplateShouldRemoveTemplate() throws Exception {
        saveTemplate("template-a", "桌游模板", "社交", List.of("桌游", "轻松"));

        mockMvc.perform(delete("/admin/activities/templates/{templateId}", "template-a")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin-a")
                                .roles("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap());

        mockMvc.perform(get("/activities/templates")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void adminActivityTemplateManagementShouldRejectPersonalUser() throws Exception {
        mockMvc.perform(post("/admin/activities/templates")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTemplateRequestJson("城市探索模板", "城市探索", 20)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdateActivityTemplateShouldRejectMissingTemplate() throws Exception {
        mockMvc.perform(put("/admin/activities/templates/{templateId}", "missing")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin-a")
                                .roles("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTemplateRequestJson("城市探索模板", "城市探索", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void createDraftFromTemplateShouldReturnPersistedDraft() throws Exception {
        saveUser("user-a");
        saveTemplate("template-a", "桌游模板", "社交", List.of("桌游", "轻松"));

        mockMvc.perform(post("/activities/templates/{templateId}/drafts", "template-a")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("桌游模板"))
                .andExpect(jsonPath("$.data.tags[0]").value("桌游"))
                .andExpect(jsonPath("$.data.reviewStatus").value("draft"));
    }

    @Test
    void createDraftFromTemplateShouldRejectMissingTemplate() throws Exception {
        saveUser("user-a");

        mockMvc.perform(post("/activities/templates/{templateId}/drafts", "missing")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void cloneActivityShouldReturnDraftForOrganizer() throws Exception {
        saveUser("user-a");
        Activity source = saveSubmittedActivity("user-a", "原活动");

        mockMvc.perform(post("/activities/{activityId}/clone", source.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("原活动"))
                .andExpect(jsonPath("$.data.reviewStatus").value("draft"));
    }

    @Test
    void cloneActivityShouldRejectNonOrganizer() throws Exception {
        saveUser("user-a");
        saveUser("user-b");
        Activity source = saveSubmittedActivity("user-a", "原活动");

        mockMvc.perform(post("/activities/{activityId}/clone", source.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("user-b")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    void updateDraftShouldRefreshTitle() throws Exception {
        saveUser("user-a");
        String responseBody = mockMvc.perform(post("/activities/drafts")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "桌游局")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String activityId = extractActivityId(responseBody);

        mockMvc.perform(patch("/activities/drafts/{activityId}", activityId)
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "更新后的活动标题")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("更新后的活动标题"));
    }

    @Test
    void saveDraftShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/activities/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "桌游局")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitActivityShouldReturnDetailWhenSuccessful() throws Exception {
        saveUser("user-a");
        String responseBody = mockMvc.perform(post("/activities/drafts")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "提交测试活动")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String activityId = extractActivityId(responseBody);

        mockMvc.perform(post("/activities/{activityId}/submit", activityId)
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").value(activityId))
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.data.aiContentReview.riskLevel").value("low"))
                .andExpect(jsonPath("$.data.organizerName").value("nickname-user-a"))
                .andExpect(jsonPath("$.data.reviewRecords.length()").value(1));
    }

    @Test
    void submitActivityShouldRejectWhenNotOwner() throws Exception {
        saveUser("user-a");
        saveUser("user-b");
        String responseBody = mockMvc.perform(post("/activities/drafts")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequestJson(List.of(), "桌游局")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String activityId = extractActivityId(responseBody);

        mockMvc.perform(post("/activities/{activityId}/submit", activityId)
                        .with(SecurityMockMvcRequestPostProcessors.user("user-b")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    void submitActivityShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/activities/{activityId}/submit", "any-id")).andExpect(status().isUnauthorized());
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

    private MediaFile saveMediaFile(UUID mediaId, String userId) {
        return mediaFileRepository.save(MediaFile.builder()
                .mediaId(mediaId)
                .fileName("cover.png")
                .contentType("image/png")
                .sizeBytes(1L)
                .usage(MediaUsage.activityImage)
                .storagePath("/tmp/" + mediaId)
                .uploadedBy(userId)
                .uploadedAt(Instant.now())
                .build());
    }

    private Activity saveSubmittedActivity(String organizerId, String title) {
        return activityRepository.save(Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title(title)
                .tags(List.of("社交", "桌游"))
                .introduction("原活动简介")
                .startAt(Instant.parse("2026-07-02T10:00:00Z"))
                .endAt(Instant.parse("2026-07-02T12:00:00Z"))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("海淀区某街道")
                .placeName("活动中心")
                .safetyNotice("原活动安全须知")
                .capacity(8)
                .registrationDeadline(Instant.parse("2026-07-01T12:00:00Z"))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.ended)
                .manualReviewRequired(false)
                .requireLocationCheck(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private ActivityTemplate saveTemplate(String templateId, String name, String activityType, List<String> tags) {
        return activityTemplateRepository.save(ActivityTemplate.builder()
                .templateId(templateId)
                .name(name)
                .activityType(activityType)
                .defaultTags(tags)
                .defaultIntroduction("模板介绍")
                .defaultSafetyNotice("模板安全须知")
                .defaultCapacity(12)
                .build());
    }

    private String createDraftRequestJson(List<UUID> imageIds, String title) {
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

    private String createTemplateRequestJson(String name, String activityType, int defaultCapacity) {
        return "{"
                + "\"name\":\"" + name + "\","
                + "\"activityType\":\"" + activityType + "\","
                + "\"defaultTags\":[\"探索\",\"拍照\"],"
                + "\"defaultIntroduction\":\"沿着城市线索完成探索任务\","
                + "\"defaultSafetyNotice\":\"注意交通安全，结伴行动\","
                + "\"defaultCapacity\":" + defaultCapacity
                + "}";
    }

    private String extractActivityId(String responseBody) {
        String marker = "\"activityId\":\"";
        int valueStartIndex = responseBody.indexOf(marker) + marker.length();
        int valueEndIndex = responseBody.indexOf('"', valueStartIndex);
        return responseBody.substring(valueStartIndex, valueEndIndex);
    }
}

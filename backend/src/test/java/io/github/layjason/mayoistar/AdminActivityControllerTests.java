package io.github.layjason.mayoistar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityReviewRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
class AdminActivityControllerTests {

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
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        activityReviewRecordRepository.deleteAll();
        activityImageRepository.deleteAll();
        activityRepository.deleteAll();
        mediaFileRepository.deleteAll();
        userRepository.deleteAll();
    }

    /* ========== getActivity ========== */

    @Test
    void getActivityShouldReturnDetail() throws Exception {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "活动");

        mockMvc.perform(get("/admin/activities/{activityId}", activity.getActivityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value(activity.getActivityId()))
                .andExpect(jsonPath("$.data.title").value("活动"))
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"));
    }

    @Test
    void getActivityShouldReturnErrorForNotFound() throws Exception {
        mockMvc.perform(get("/admin/activities/{activityId}", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(60008))
                .andExpect(jsonPath("$.message").value("Activity {activityId} does not exist"));
    }

    /* ========== reviewActivity ========== */

    @Test
    void reviewActivityShouldApprove() throws Exception {
        User organizer = saveUser("user-a");
        Activity activity = savePendingActivity(organizer.getUserId(), "待审核");

        AdminDtos.ReviewDecisionRequest request = new AdminDtos.ReviewDecisionRequest();
        request.setResult(ReviewStatus.approved);

        mockMvc.perform(post("/admin/activities/{activityId}/review", activity.getActivityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.data.runtimeStatus").value("notStarted"));
    }

    @Test
    void reviewActivityShouldRejectWithReason() throws Exception {
        User organizer = saveUser("user-a");
        Activity activity = savePendingActivity(organizer.getUserId(), "待审核");

        AdminDtos.ReviewDecisionRequest request = new AdminDtos.ReviewDecisionRequest();
        request.setResult(ReviewStatus.rejected);
        request.setReason("违规内容");

        mockMvc.perform(post("/admin/activities/{activityId}/review", activity.getActivityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reviewStatus").value("rejected"));
    }

    /* ========== takeDownActivity ========== */

    @Test
    void takeDownActivityShouldSucceed() throws Exception {
        User organizer = saveUser("user-a");
        Activity activity = saveApprovedActivity(organizer.getUserId(), "已发布");

        AdminDtos.ActivityModerationRequest request = new AdminDtos.ActivityModerationRequest();
        request.setReason("违规内容");

        mockMvc.perform(post("/admin/activities/{activityId}/take-down", activity.getActivityId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.runtimeStatus").value("takenDown"));
    }

    /* ========== restoreActivity ========== */

    @Test
    void restoreActivityShouldSucceed() throws Exception {
        User organizer = saveUser("user-a");
        Activity activity = saveTakenDownActivity(organizer.getUserId());

        mockMvc.perform(post("/admin/activities/{activityId}/restore", activity.getActivityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.runtimeStatus").value("notStarted"));
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

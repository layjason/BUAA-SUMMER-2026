package io.github.layjason.mayoistar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
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
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivityQueryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityImageRepository activityImageRepository;

    @Autowired
    private ActivityReviewRecordRepository activityReviewRecordRepository;

    @Autowired
    private ActivityRegistrationRepository activityRegistrationRepository;

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
        activityRegistrationRepository.deleteAll();
        activityReviewRecordRepository.deleteAll();
        activityImageRepository.deleteAll();
        activityRepository.deleteAll();
        mediaFileRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getActivityShouldReturnDetailForApproved() throws Exception {
        saveUser("user-a");
        Activity activity = saveApprovedActivity("user-a", "公开活动");

        mockMvc.perform(get("/activities/{activityId}", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").value(activity.getActivityId()))
                .andExpect(jsonPath("$.data.title").value("公开活动"))
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.data.organizerName").value("nickname-user-a"));
    }

    @Test
    void getActivityShouldReturnBusinessCodeForInvisible() throws Exception {
        saveUser("user-a");
        Activity activity = saveDraftActivity("user-a", "私密草稿");

        mockMvc.perform(get("/activities/{activityId}", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20002))
                .andExpect(jsonPath("$.message").value("Activity {activityId} is not visible"));
    }

    @Test
    void getActivityShouldReturnOwnDraftWhenAuthenticated() throws Exception {
        saveUser("user-a");
        Activity activity = saveDraftActivity("user-a", "我的草稿");

        mockMvc.perform(get("/activities/{activityId}", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityId").value(activity.getActivityId()))
                .andExpect(jsonPath("$.data.reviewStatus").value("draft"));
    }

    @Test
    void getActivityShouldRejectNonOwnerForDraft() throws Exception {
        saveUser("user-a");
        saveUser("user-b");
        Activity activity = saveDraftActivity("user-a", "草稿");

        mockMvc.perform(get("/activities/{activityId}", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("user-b")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20002));
    }

    @Test
    void listMyActivitiesShouldReturnUserActivities() throws Exception {
        saveUser("user-a");
        saveUser("user-b");
        saveApprovedActivity("user-a", "A的活动");
        saveApprovedActivity("user-b", "B的活动");

        mockMvc.perform(get("/activities/mine")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("A的活动"));
    }

    @Test
    void listMyActivitiesShouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/activities/mine")).andExpect(status().isForbidden());
    }

    @Test
    void listMyActivitiesShouldFilterByStatus() throws Exception {
        saveUser("user-a");
        saveApprovedActivity("user-a", "已通过");
        saveDraftActivity("user-a", "草稿");

        mockMvc.perform(get("/activities/mine")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .param("status", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reviewStatus").value("draft"));
    }

    @Test
    void listMyRegistrationsShouldReturnUserRegistrations() throws Exception {
        saveUser("user-a");
        saveUser("user-b");
        saveUser("organizer");
        Activity myActivity = saveApprovedActivity("organizer", "我的报名活动");
        Activity otherActivity = saveApprovedActivity("organizer", "别人的报名活动");
        saveRegistration(
                myActivity.getActivityId(),
                "user-a",
                RegistrationStatus.waiting,
                3,
                Instant.parse("2026-07-02T09:00:00Z"),
                null);
        saveRegistration(
                otherActivity.getActivityId(),
                "user-b",
                RegistrationStatus.registered,
                null,
                Instant.parse("2026-07-02T10:00:00Z"),
                null);

        mockMvc.perform(get("/activities/registrations/mine")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].activityId").value(myActivity.getActivityId()))
                .andExpect(jsonPath("$.data.items[0].title").value("我的报名活动"))
                .andExpect(jsonPath("$.data.items[0].registrationStatus").value("waiting"))
                .andExpect(jsonPath("$.data.items[0].waitingRank").value(3));
    }

    @Test
    void getActivityShouldReturnReviewStatusAndRuntimeStatus() throws Exception {
        saveUser("user-a");
        Activity activity = saveApprovedActivity("user-a", "状态测试");

        mockMvc.perform(get("/activities/{activityId}", activity.getActivityId())
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer")
                                .roles("personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.data.runtimeStatus").value("notStarted"));
    }

    private ActivityRegistration saveRegistration(
            String activityId,
            String userId,
            RegistrationStatus status,
            Integer waitingRank,
            Instant registeredAt,
            Instant confirmationDeadline) {
        return activityRegistrationRepository.save(ActivityRegistration.builder()
                .registrationId(UUID.randomUUID().toString())
                .activityId(activityId)
                .userId(userId)
                .status(status)
                .participantNote("测试备注")
                .acceptedSafetyNotice(true)
                .waitingRank(waitingRank)
                .confirmationDeadline(confirmationDeadline)
                .registeredAt(registeredAt)
                .build());
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

    private Activity saveDraftActivity(String organizerId, String title) {
        return activityRepository.save(Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title(title)
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
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }
}

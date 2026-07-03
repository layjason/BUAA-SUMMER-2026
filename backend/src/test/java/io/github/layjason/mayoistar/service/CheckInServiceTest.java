package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.config.CheckInProperties;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * CheckInService 单元测试。
 *
 * <p>类职责：验证签到二维码生成、扫码签到、签到列表查询和导出逻辑。
 *
 * <p>不变量：所有测试不访问数据库，不依赖真实文件存储。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInService")
class CheckInServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityRegistrationRepository registrationRepository;

    private CheckInProperties properties;
    private CheckInService checkInService;

    private static final String ORGANIZER_ID = "org-001";
    private static final String ACTIVITY_ID = "act-001";
    private static final String USER_ID = "user-001";
    private static final String REGISTRATION_ID = "reg-001";

    @BeforeEach
    void setUp() {
        properties = new CheckInProperties();
        properties.setSigningSecret("test-checkin-secret-for-unit-tests");
        properties.setTokenExpireSeconds(900);
        properties.setLocationCheckMeters(500);
        checkInService = new CheckInService(properties, activityRepository, registrationRepository);
    }

    /* ========== 生成签到二维码 ========== */

    @Nested
    @DisplayName("generateCheckInQrCode")
    class GenerateCheckInQrCode {

        @Test
        @DisplayName("活动发起人可生成签到二维码")
        void organizerCanGenerateQrCode() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));

            ActivityDtos.CheckInQrCode result = checkInService.generateCheckInQrCode(ORGANIZER_ID, ACTIVITY_ID);

            assertThat(result.getActivityId()).isEqualTo(ACTIVITY_ID);
            assertThat(result.getQrCodeToken()).isNotBlank();
            assertThat(result.getExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("非活动发起人无权生成签到二维码")
        void nonOrganizerCannotGenerateQrCode() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> checkInService.generateCheckInQrCode("other-user", ACTIVITY_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20003);
        }

        @Test
        @DisplayName("活动不存在时抛出异常")
        void activityNotFoundThrowsException() {
            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> checkInService.generateCheckInQrCode(ORGANIZER_ID, ACTIVITY_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20002);
        }

        @Test
        @DisplayName("活动已结束时无法生成")
        void endedActivityCannotGenerate() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ended);
            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> checkInService.generateCheckInQrCode(ORGANIZER_ID, ACTIVITY_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20013);
        }

        @Test
        @DisplayName("活动已下架时无法生成")
        void takenDownActivityCannotGenerate() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.takenDown);
            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> checkInService.generateCheckInQrCode(ORGANIZER_ID, ACTIVITY_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20013);
        }
    }

    /* ========== 扫码签到 ========== */

    @Nested
    @DisplayName("checkIn")
    class CheckIn {

        @Test
        @DisplayName("有效 token 和报名状态下签到成功")
        void validTokenAndRegisteredStatusCheckInSuccessfully() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            ActivityRegistration registration = buildRegistration(RegistrationStatus.registered);
            String token = buildValidToken(ACTIVITY_ID);

            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
            when(registrationRepository.findByActivityIdAndUserId(ACTIVITY_ID, USER_ID))
                    .thenReturn(Optional.of(registration));
            when(registrationRepository.save(any())).thenReturn(registration);

            ActivityDtos.CheckInRecord result = checkInService.checkIn(USER_ID, ACTIVITY_ID, token, null);

            assertThat(result.getRegistrationStatus()).isEqualTo(RegistrationStatus.checkedIn);
            assertThat(result.getCheckedInAt()).isNotNull();

            ArgumentCaptor<ActivityRegistration> captor = ArgumentCaptor.forClass(ActivityRegistration.class);
            verify(registrationRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(RegistrationStatus.checkedIn);
            assertThat(captor.getValue().getCheckedInAt()).isNotNull();
        }

        @Test
        @DisplayName("签名无效的 token 抛出异常")
        void invalidTokenSignatureThrowsException() {
            String invalidToken = "invalid-payload.wrong-signature";

            assertThatThrownBy(() -> checkInService.checkIn(USER_ID, ACTIVITY_ID, invalidToken, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20013);
        }

        @Test
        @DisplayName("token 对应不同活动时抛出异常")
        void tokenForDifferentActivityThrowsException() {
            String tokenForOtherActivity = buildValidToken("act-other");

            assertThatThrownBy(() -> checkInService.checkIn(USER_ID, ACTIVITY_ID, tokenForOtherActivity, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20013);
        }

        @Test
        @DisplayName("未报名用户无法签到")
        void userNotRegisteredCannotCheckIn() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            String token = buildValidToken(ACTIVITY_ID);

            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
            when(registrationRepository.findByActivityIdAndUserId(ACTIVITY_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> checkInService.checkIn(USER_ID, ACTIVITY_ID, token, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20011);
        }

        @Test
        @DisplayName("非 registered 状态无法签到")
        void nonRegisteredStatusCannotCheckIn() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            ActivityRegistration registration = buildRegistration(RegistrationStatus.canceled);
            String token = buildValidToken(ACTIVITY_ID);

            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
            when(registrationRepository.findByActivityIdAndUserId(ACTIVITY_ID, USER_ID))
                    .thenReturn(Optional.of(registration));

            assertThatThrownBy(() -> checkInService.checkIn(USER_ID, ACTIVITY_ID, token, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20013);
        }

        @Test
        @DisplayName("位置校验不通过时抛出异常")
        void locationCheckFailureThrowsException() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            activity.setPointLat(39.9042);
            activity.setPointLon(116.4074);
            ActivityRegistration registration = buildRegistration(RegistrationStatus.registered);
            String token = buildValidToken(ACTIVITY_ID);

            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
            when(registrationRepository.findByActivityIdAndUserId(ACTIVITY_ID, USER_ID))
                    .thenReturn(Optional.of(registration));

            // 远距离位置（1000 公里外）
            var geoPoint = new io.github.layjason.mayoistar.api.common.CommonDtos.GeoPoint();
            geoPoint.setLatitude(31.2304);
            geoPoint.setLongitude(121.4737);

            ActivityDtos.CheckInRequest request = new ActivityDtos.CheckInRequest();
            request.setQrCodeToken(token);
            request.setCurrentLocation(geoPoint);

            assertThatThrownBy(() -> checkInService.checkIn(USER_ID, ACTIVITY_ID, token, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20014);
        }

        @Test
        @DisplayName("位置在允许范围内签到成功")
        void locationWithinRangeCheckInSuccessfully() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            activity.setPointLat(39.9042);
            activity.setPointLon(116.4074);
            ActivityRegistration registration = buildRegistration(RegistrationStatus.registered);
            String token = buildValidToken(ACTIVITY_ID);

            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
            when(registrationRepository.findByActivityIdAndUserId(ACTIVITY_ID, USER_ID))
                    .thenReturn(Optional.of(registration));
            when(registrationRepository.save(any())).thenReturn(registration);

            // 临近位置（~100 米）
            var geoPoint = new io.github.layjason.mayoistar.api.common.CommonDtos.GeoPoint();
            geoPoint.setLatitude(39.9048);
            geoPoint.setLongitude(116.4080);

            ActivityDtos.CheckInRequest request = new ActivityDtos.CheckInRequest();
            request.setQrCodeToken(token);
            request.setCurrentLocation(geoPoint);

            ActivityDtos.CheckInRecord result = checkInService.checkIn(USER_ID, ACTIVITY_ID, token, request);

            assertThat(result.getRegistrationStatus()).isEqualTo(RegistrationStatus.checkedIn);
        }
    }

    /* ========== 签到列表查询 ========== */

    @Nested
    @DisplayName("listCheckIns")
    class ListCheckIns {

        @Test
        @DisplayName("活动发起人可查看签到列表")
        void organizerCanViewCheckInList() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            ActivityRegistration registration = buildRegistrationWithUser(RegistrationStatus.checkedIn);
            Page<ActivityRegistration> page = new PageImpl<>(List.of(registration));

            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
            when(registrationRepository.findByActivityId(eq(ACTIVITY_ID), any(Pageable.class)))
                    .thenReturn(page);

            PageResult<ActivityDtos.CheckInRecord> result =
                    checkInService.listCheckIns(ORGANIZER_ID, ACTIVITY_ID, 1, 20);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getUserId()).isEqualTo(USER_ID);
            assertThat(result.getItems().get(0).getRegistrationStatus()).isEqualTo(RegistrationStatus.checkedIn);
        }

        @Test
        @DisplayName("非发起人无法查看签到列表")
        void nonOrganizerCannotViewCheckInList() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> checkInService.listCheckIns("other-user", ACTIVITY_ID, 1, 20))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20003);
        }
    }

    /* ========== 签到数据导出 ========== */

    @Nested
    @DisplayName("exportCheckIns")
    class ExportCheckIns {

        @Test
        @DisplayName("活动发起人可导出签到数据")
        void organizerCanExportCheckInData() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            ActivityRegistration registration = buildRegistrationWithUser(RegistrationStatus.checkedIn);
            registration.setCheckedInAt(Instant.parse("2026-07-01T10:00:00Z"));
            registration.setRegisteredAt(Instant.parse("2026-07-01T09:00:00Z"));

            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
            when(registrationRepository.findByActivityIdOrderByCheckedInAtAsc(ACTIVITY_ID))
                    .thenReturn(List.of(registration));

            byte[] csvData = checkInService.exportCheckIns(ORGANIZER_ID, ACTIVITY_ID);

            String csvContent = new String(csvData, StandardCharsets.UTF_8);
            assertThat(csvContent).contains("报名ID,用户ID,昵称,报名状态,报名时间,签到时间");
            assertThat(csvContent).contains(REGISTRATION_ID);
            assertThat(csvContent).contains(USER_ID);
            assertThat(csvContent).contains("checkedIn");
        }

        @Test
        @DisplayName("非发起人无法导出签到数据")
        void nonOrganizerCannotExportCheckInData() {
            Activity activity = buildActivity(ORGANIZER_ID, ActivityRuntimeStatus.ongoing);
            when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> checkInService.exportCheckIns("other-user", ACTIVITY_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(20003);
        }
    }

    /* ========== 辅助方法 ========== */

    /**
     * 构建测试活动。
     */
    private Activity buildActivity(String organizerId, ActivityRuntimeStatus runtimeStatus) {
        Activity activity = new Activity();
        activity.setActivityId(ACTIVITY_ID);
        activity.setOrganizerId(organizerId);
        activity.setTitle("测试活动");
        activity.setRuntimeStatus(runtimeStatus);
        activity.setCapacity(10);
        activity.setStartAt(Instant.parse("2026-07-01T09:00:00Z"));
        activity.setEndAt(Instant.parse("2026-07-01T12:00:00Z"));
        activity.setCreatedAt(Instant.now());
        activity.setUpdatedAt(Instant.now());
        return activity;
    }

    /**
     * 构建测试报名记录（不含 User）。
     */
    private ActivityRegistration buildRegistration(RegistrationStatus status) {
        ActivityRegistration registration = new ActivityRegistration();
        registration.setRegistrationId(REGISTRATION_ID);
        registration.setActivityId(ACTIVITY_ID);
        registration.setUserId(USER_ID);
        registration.setStatus(status);
        registration.setAcceptedSafetyNotice(true);
        registration.setRegisteredAt(Instant.parse("2026-07-01T09:00:00Z"));
        return registration;
    }

    /**
     * 构建含 User 的测试报名记录。
     */
    private ActivityRegistration buildRegistrationWithUser(RegistrationStatus status) {
        ActivityRegistration registration = buildRegistration(status);
        User user = new User();
        user.setUserId(USER_ID);
        user.setNickname("测试用户");
        user.setEmail("test@example.com");
        registration.setUser(user);
        return registration;
    }

    /**
     * 生成有效的签到 QR 码 token（用于测试）。
     */
    private String buildValidToken(String activityId) {
        try {
            long expiresAtEpoch = Instant.now().plusSeconds(900).getEpochSecond();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = activityId + "\n" + expiresAtEpoch;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

            String tokenPayload = activityId + "|" + expiresAtEpoch;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenPayload.getBytes(StandardCharsets.UTF_8))
                    + "."
                    + Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

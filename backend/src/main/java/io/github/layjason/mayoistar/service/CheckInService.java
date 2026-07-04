package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.activities.ActivityDtos.CheckInRecord;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.config.CheckInProperties;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.social.TeamPointChangeSource;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 签到业务服务。
 *
 * <p>类职责：处理签到二维码生成、扫码签到、签到列表查询和签到数据导出。
 *
 * <p>不变量：签到二维码 token 基于 HMAC-SHA256 签名，包含活动标识和过期时间；
 * 只有已报名（registered）用户可以签到，签到后状态变为 checkedIn。
 */
@Slf4j
@Service
public class CheckInService {

    private final CheckInProperties properties;
    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository registrationRepository;
    private final TeamPointService teamPointService;

    /**
     * @param properties             签到配置属性
     * @param activityRepository     活动数据访问
     * @param registrationRepository 报名数据访问
     * @param teamPointService      小队积分服务
     */
    public CheckInService(
            CheckInProperties properties,
            ActivityRepository activityRepository,
            ActivityRegistrationRepository registrationRepository,
            TeamPointService teamPointService) {
        this.properties = properties;
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.teamPointService = teamPointService;
    }

    /**
     * 生成签到二维码 token。
     *
     * <p>前置条件：userId 为活动发起人，activityId 对应的活动存在且未结束或被下架。
     *
     * <p>后置条件：返回包含签名 token 和过期时间的 CheckInQrCode。
     *
     * <p>行为：
     * <ul>
     *   <li>活动不存在或不可见 → ErrorCodes.ACTIVITY_NOT_VISIBLE</li>
     *   <li>调用方不是活动发起人 → ErrorCodes.ACTIVITY_PERMISSION_DENIED</li>
     *   <li>活动已结束或已下架 → ErrorCodes.CHECK_IN_QR_CODE_INVALID</li>
     * </ul>
     *
     * @param userId     调用方用户 ID
     * @param activityId 活动 ID
     * @return 签到二维码信息
     */
    public ActivityDtos.CheckInQrCode generateCheckInQrCode(@NonNull String userId, @NonNull String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity " + activityId + " is not visible"));

        if (!Objects.equals(activity.getOrganizerId(), userId)) {
            throw new BusinessException(ErrorCodes.ACTIVITY_PERMISSION_DENIED, "无权生成签到二维码");
        }

        if (activity.getRuntimeStatus() == ActivityRuntimeStatus.ended
                || activity.getRuntimeStatus() == ActivityRuntimeStatus.takenDown) {
            throw new BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID, "当前活动状态不允许生成签到二维码");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getTokenExpireSeconds());
        long expiresAtEpoch = expiresAt.getEpochSecond();

        byte[] signature = createSignature(activityId, expiresAtEpoch);
        // token 格式: base64(activityId|expiresAtEpoch).base64(rawSignature)
        String payload = activityId + "|" + expiresAtEpoch;
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        ActivityDtos.CheckInQrCode result = new ActivityDtos.CheckInQrCode();
        result.setActivityId(activityId);
        result.setQrCodeToken(token);
        result.setExpiresAt(expiresAt.toString());

        log.info("签到二维码已生成: activityId={}, userId={}, expiresAt={}", activityId, userId, expiresAt);
        return result;
    }

    /**
     * 扫码签到。
     *
     * <p>前置条件：qrCodeToken 有效且未过期，userId 已报名该活动且状态为 registered。
     *
     * <p>后置条件：报名状态变为 checkedIn，checkedInAt 设置为当前时间。
     *
     * <p>行为：
     * <ul>
     *   <li>token 签名无效或过期 → ErrorCodes.CHECK_IN_QR_CODE_INVALID</li>
     *   <li>用户未报名 → ErrorCodes.REGISTRATION_NOT_FOUND</li>
     *   <li>用户非 registered 状态 → ErrorCodes.CHECK_IN_QR_CODE_INVALID</li>
     *   <li>活动要求位置校验但用户未提供 → ErrorCodes.CHECK_IN_LOCATION_REQUIRED</li>
     *   <li>位置校验不通过 → ErrorCodes.CHECK_IN_LOCATION_INVALID</li>
     * </ul>
     *
     * @param userId          签到用户 ID
     * @param activityId      活动 ID
     * @param qrCodeToken     二维码 token
     * @param currentLocation 当前位置（可为 null，为 null 时跳过位置校验）
     * @return 签到记录
     */
    @Transactional
    public CheckInRecord checkIn(
            @NonNull String userId,
            @NonNull String activityId,
            @NonNull String qrCodeToken,
            @Nullable ActivityDtos.CheckInRequest currentLocationHolder) {
        // 验证 token
        validateQRCodeToken(activityId, qrCodeToken);

        // 检查活动是否可见
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity " + activityId + " is not visible"));

        // 查找报名记录（加悲观写锁，防止并发重复签到）
        ActivityRegistration registration = registrationRepository
                .findByActivityIdAndUserIdForUpdate(activityId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.REGISTRATION_NOT_FOUND, "未报名该活动，无法签到"));

        // 检查报名状态
        if (registration.getStatus() != RegistrationStatus.registered) {
            throw new BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID, "签到二维码无效");
        }

        // 位置校验
        // 若活动发起人设置了要求位置校验，则用户必须提供有效位置信息并进行距离校验
        if (Boolean.TRUE.equals(activity.getRequireLocationCheck())) {
            if (currentLocationHolder == null || currentLocationHolder.getCurrentLocation() == null) {
                throw new BusinessException(ErrorCodes.CHECK_IN_LOCATION_REQUIRED, "该活动要求签到位置校验，请提供当前位置信息");
            }
            validateLocation(activity, currentLocationHolder.getCurrentLocation());
        } else if (currentLocationHolder != null && currentLocationHolder.getCurrentLocation() != null) {
            // 活动不要求位置校验时，若用户主动提供了位置，仍进行校验
            validateLocation(activity, currentLocationHolder.getCurrentLocation());
        }

        // 执行签到
        Instant now = Instant.now();
        registration.setStatus(RegistrationStatus.checkedIn);
        registration.setCheckedInAt(now);
        registrationRepository.save(registration);

        log.info(
                "签到成功: activityId={}, userId={}, registrationId={}",
                activityId,
                userId,
                registration.getRegistrationId());

        // 小队活动签到加分
        if (activity.getTeamId() != null) {
            try {
                teamPointService.addPoints(
                        activity.getTeamId(),
                        userId,
                        TeamPointService.CHECK_IN_POINTS,
                        TeamPointChangeSource.checkin,
                        registration.getRegistrationId(),
                        "活动签到");
            } catch (BusinessException e) {
                log.info(
                        "签到加分跳过: activityId={}, userId={}, code={}",
                        activityId,
                        userId,
                        e.getCode());
            } catch (Exception e) {
                log.warn("签到加分失败: activityId={}, userId={}", activityId, userId, e);
            }
        }

        return toCheckInRecord(registration);
    }

    /**
     * 查看签到列表。
     *
     * <p>前置条件：userId 为活动发起人或管理员，activityId 对应的活动存在。
     *
     * <p>后置条件：返回分页的签到记录列表。
     *
     * <p>行为：
     * <ul>
     *   <li>活动不存在或不可见 → ErrorCodes.ACTIVITY_NOT_VISIBLE</li>
     *   <li>调用方不是活动发起人 → ErrorCodes.ACTIVITY_PERMISSION_DENIED</li>
     * </ul>
     *
     * @param userId     调用方用户 ID
     * @param activityId 活动 ID
     * @param page       页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 分页签到记录
     */
    @Transactional(readOnly = true)
    public PageResult<CheckInRecord> listCheckIns(
            @NonNull String userId, @NonNull String activityId, @Nullable Integer page, @Nullable Integer pageSize) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity " + activityId + " is not visible"));

        if (!Objects.equals(activity.getOrganizerId(), userId)) {
            throw new BusinessException(ErrorCodes.ACTIVITY_PERMISSION_DENIED, "无权查看签到列表");
        }

        int pageNum = page != null && page > 0 ? page : 1;
        int size = pageSize != null && pageSize > 0 ? Math.min(pageSize, 100) : 20;
        PageRequest pageRequest = PageRequest.of(pageNum - 1, size, Sort.by(Sort.Direction.DESC, "checkedInAt"));

        var dbPage = registrationRepository.findByActivityId(activityId, pageRequest);
        List<CheckInRecord> records =
                dbPage.getContent().stream().map(this::toCheckInRecord).toList();

        return new PageResult<>(records, dbPage.getTotalElements(), pageNum, size, (int)
                Math.ceil((double) dbPage.getTotalElements() / size));
    }

    /**
     * 导出签到数据为 CSV。
     *
     * <p>前置条件：userId 为活动发起人或管理员，activityId 对应的活动存在。
     *
     * <p>后置条件：返回 CSV 格式的签到数据字节数组。
     *
     * <p>行为：
     * <ul>
     *   <li>活动不存在或不可见 → ErrorCodes.ACTIVITY_NOT_VISIBLE</li>
     *   <li>调用方不是活动发起人 → ErrorCodes.ACTIVITY_PERMISSION_DENIED</li>
     * </ul>
     *
     * @param userId     调用方用户 ID
     * @param activityId 活动 ID
     * @return CSV 字节数组
     */
    @Transactional(readOnly = true)
    public byte[] exportCheckIns(@NonNull String userId, @NonNull String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity " + activityId + " is not visible"));

        if (!Objects.equals(activity.getOrganizerId(), userId)) {
            throw new BusinessException(ErrorCodes.ACTIVITY_PERMISSION_DENIED, "无权导出签到数据");
        }

        List<ActivityRegistration> registrations =
                registrationRepository.findByActivityIdOrderByCheckedInAtAsc(activityId);

        StringBuilder csv = new StringBuilder();
        // BOM for Excel UTF-8 compatibility
        csv.append('\uFEFF');
        csv.append("报名ID,用户ID,昵称,报名状态,报名时间,签到时间\n");

        for (ActivityRegistration reg : registrations) {
            csv.append(escapeCsv(reg.getRegistrationId())).append(',');
            csv.append(escapeCsv(reg.getUserId())).append(',');
            csv.append(escapeCsv(reg.getUser() != null ? reg.getUser().getNickname() : ""))
                    .append(',');
            csv.append(escapeCsv(reg.getStatus().name())).append(',');
            csv.append(escapeCsv(
                            reg.getRegisteredAt() != null
                                    ? reg.getRegisteredAt().toString()
                                    : ""))
                    .append(',');
            csv.append(escapeCsv(
                            reg.getCheckedInAt() != null ? reg.getCheckedInAt().toString() : ""))
                    .append('\n');
        }

        log.info("签到数据已导出: activityId={}, userId={}, 记录数={}", activityId, userId, registrations.size());
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /* ========== 内部辅助方法 ========== */

    /**
     * 验证签到二维码 token。
     *
     * <p>前置条件：token 非空，activityId 非空。
     *
     * <p>后置条件：签名有效且未过期时通过；否则抛出 BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID)。
     *
     * @param activityId 活动 ID
     * @param token      二维码 token
     */
    private void validateQRCodeToken(String activityId, String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID, "签到二维码无效");
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\|");
            if (payloadParts.length != 2) {
                throw new BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID, "签到二维码无效");
            }

            String tokenActivityId = payloadParts[0];
            long expiresAtEpoch;
            try {
                expiresAtEpoch = Long.parseLong(payloadParts[1]);
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID, "签到二维码无效");
            }

            // 检查活动 ID 是否匹配
            if (!tokenActivityId.equals(activityId)) {
                throw new BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID, "签到二维码无效");
            }

            // 检查是否过期
            if (Instant.now().getEpochSecond() > expiresAtEpoch) {
                throw new BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID, "签到二维码无效");
            }

            // 验证签名
            byte[] expectedSig = createSignature(activityId, expiresAtEpoch);
            byte[] actualSig = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                throw new BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID, "签到二维码无效");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCodes.CHECK_IN_QR_CODE_INVALID, "签到二维码无效");
        }
    }

    /**
     * 验证签到位置是否在活动地点附近。
     *
     * <p>前置条件：activity 有有效的位置坐标，currentLocation 非空。
     *
     * <p>后置条件：距离在允许范围内时通过；否则抛出 BusinessException(ErrorCodes.CHECK_IN_LOCATION_INVALID)。
     *
     * @param activity        活动实体
     * @param currentLocation 用户当前位置
     */
    private void validateLocation(
            Activity activity, io.github.layjason.mayoistar.api.common.CommonDtos.GeoPoint currentLocation) {
        if (activity.getPointLat() == null || activity.getPointLon() == null) {
            // 活动没有设置位置坐标，跳过位置校验
            return;
        }

        if (currentLocation.getLatitude() == null || currentLocation.getLongitude() == null) {
            // 没有有效的位置信息，跳过位置校验
            return;
        }

        double distance = haversineDistance(
                activity.getPointLat(), activity.getPointLon(),
                currentLocation.getLatitude(), currentLocation.getLongitude());

        if (distance > properties.getLocationCheckMeters()) {
            log.warn(
                    "签到位置校验失败: activityId={}, 距离={}米, 允许最大距离={}米",
                    activity.getActivityId(),
                    String.format("%.0f", distance),
                    properties.getLocationCheckMeters());
            throw new BusinessException(ErrorCodes.CHECK_IN_LOCATION_INVALID, "签到位置不在活动地点附近");
        }
    }

    /**
     * 使用 Haversine 公式计算两点间距离（米）。
     *
     * @param lat1 点 1 纬度
     * @param lon1 点 1 经度
     * @param lat2 点 2 纬度
     * @param lon2 点 2 经度
     * @return 距离（米）
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_M = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /**
     * 生成 HMAC-SHA256 签名。
     *
     * @param activityId   活动 ID
     * @param expiresEpoch 过期时间（epoch 秒）
     * @return HMAC 原始签名字节
     */
    private byte[] createSignature(String activityId, long expiresEpoch) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = activityId + "\n" + expiresEpoch;
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("签到 token 签名生成失败", e);
        }
    }

    /**
     * 将报名实体转换为签到记录 DTO。
     *
     * @param registration 报名实体
     * @return 签到记录 DTO
     */
    private CheckInRecord toCheckInRecord(ActivityRegistration registration) {
        CheckInRecord dto = new CheckInRecord();
        dto.setRegistrationId(registration.getRegistrationId());
        dto.setUserId(registration.getUserId());
        dto.setNickname(registration.getUser() != null ? registration.getUser().getNickname() : "");
        dto.setRegistrationStatus(registration.getStatus());
        if (registration.getCheckedInAt() != null) {
            dto.setCheckedInAt(registration.getCheckedInAt().toString());
        }
        return dto;
    }

    /**
     * CSV 字段转义。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

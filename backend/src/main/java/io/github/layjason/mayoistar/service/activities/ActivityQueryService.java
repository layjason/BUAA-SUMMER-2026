package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ActivityReviewRecordRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.MerchantProfileRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动查询服务。
 *
 * <p>类职责：处理活动详情查询和我的活动列表的读取逻辑，控制不同审核/运行状态下活动的可见性。
 *
 * <p>类不变量：所有查询为只读操作，不修改活动状态或任何持久化数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityQueryService {

    private final ActivityRepository activityRepository;
    private final ActivityReviewRecordRepository activityReviewRecordRepository;
    private final UserRepository userRepository;
    private final ActivityDtoMapper activityDtoMapper;
    private final ActivityRegistrationCountService activityRegistrationCountService;
    private final ActivityMediaQueryService activityMediaQueryService;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final PersonalProfileRepository personalProfileRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final MediaFileRepository mediaFileRepository;
    private final MediaAccessService mediaAccessService;

    /**
     * 查询活动详情。
     *
     * <p>前置条件：activityId 非空。
     *
     * <p>后置条件：当活动存在且调用方可查看时返回详情；
     * 草稿/审核中/驳回/要求修改仅发起人可见；已下架仅发起人可见；
     * 审核通过且未下架的活动所有人可见。
     *
     * <p>不变量：不修改任何持久化数据。
     *
     * @param userId 当前调用者 ID，未认证时为空
     * @param activityId 活动 ID
     * @return 活动详情
     * @throws BusinessException ErrorCodes.ACTIVITY_NOT_VISIBLE 活动不存在或不可见
     */
    @Transactional(readOnly = true)
    public ActivityDtos.ActivityDetail getActivity(Optional<String> userId, String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity {activityId} is not visible"));
        checkVisibility(userId, activity);

        ActivityDtos.ActivityDetail detail = loadActivityDetail(activity);
        log.debug("已查询活动详情，activityId={}, userId={}", activityId, userId.orElse("匿名"));
        return detail;
    }

    /**
     * 查询当前用户创建的活动列表。
     *
     * <p>前置条件：userId 非空（调用方已认证）。
     *
     * <p>后置条件：分页返回 userId 创建的活动摘要，按更新时间倒序。
     * 若传入 status 参数则仅返回指定审核状态的活动。
     *
     * <p>不变量：不修改任何持久化数据。
     *
     * @param userId 当前调用者 ID
     * @param status 可选审核状态筛选
     * @param page 页码
     * @param pageSize 每页大小
     * @return 活动摘要分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivitySummary> listMyActivities(
            String userId, String status, Integer page, Integer pageSize) {
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        PageRequest pageRequest = PageRequest.of(resolvedPage - 1, resolvedPageSize);

        Page<Activity> activityPage;
        if (status != null && !status.isBlank()) {
            ActivityReviewStatus reviewStatus = parseReviewStatus(status);
            activityPage = activityRepository.findByOrganizerIdAndReviewStatusOrderByUpdatedAtDesc(
                    userId, reviewStatus, pageRequest);
        } else {
            activityPage = activityRepository.findByOrganizerIdOrderByUpdatedAtDesc(userId, pageRequest);
        }

        log.debug(
                "已查询我的活动列表，userId={}, status={}, page={}, pageSize={}, total={}",
                sanitizeForLog(userId),
                sanitizeForLog(status),
                resolvedPage,
                resolvedPageSize,
                activityPage.getTotalElements());
        Map<String, ActivityRegistrationCounts> countsByActivityId = loadCounts(activityPage.getContent());
        return activityDtoMapper.toActivitySummaryPage(
                activityPage,
                activityMediaQueryService::loadCoverImage,
                activityId -> countsByActivityId.getOrDefault(activityId, ActivityRegistrationCounts.zero()));
    }

    /**
     * 查询当前用户报名的活动列表。
     *
     * <p>前置条件：userId 非空（调用方已认证）。
     *
     * <p>后置条件：分页返回 userId 的报名记录及对应活动摘要，按报名时间倒序。
     *
     * <p>不变量：不修改任何持久化数据。
     *
     * @param userId 当前调用者 ID
     * @param page 页码
     * @param pageSize 每页大小
     * @return 当前用户报名活动摘要分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.RegisteredActivitySummary> listMyRegistrations(
            String userId, Integer page, Integer pageSize) {
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        PageRequest pageRequest = PageRequest.of(resolvedPage - 1, resolvedPageSize);

        Page<ActivityRegistration> registrationPage =
                activityRegistrationRepository.findByUserIdOrderByRegisteredAtDesc(userId, pageRequest);
        Map<String, ActivityRegistrationCounts> countsByActivityId =
                activityRegistrationCountService.countByActivityIds(registrationPage.getContent().stream()
                        .map(ActivityRegistration::getActivityId)
                        .toList());
        List<ActivityDtos.RegisteredActivitySummary> items = registrationPage.getContent().stream()
                .map(registration -> activityDtoMapper.toRegisteredActivitySummary(
                        registration,
                        activityMediaQueryService::loadCoverImage,
                        countsByActivityId.getOrDefault(
                                registration.getActivityId(), ActivityRegistrationCounts.zero())))
                .toList();

        log.debug(
                "已查询我的报名列表，userId={}, page={}, pageSize={}, total={}",
                sanitizeForLog(userId),
                resolvedPage,
                resolvedPageSize,
                registrationPage.getTotalElements());
        return new PageResult<>(
                items,
                registrationPage.getTotalElements(),
                registrationPage.getNumber() + 1,
                registrationPage.getSize(),
                registrationPage.getTotalPages());
    }

    /**
     * 查询活动参与者列表。
     *
     * <p>前置条件：activityId 对应活动存在；调用方是管理员、活动发起人或已加入该活动的用户。
     *
     * <p>后置条件：分页返回活动报名记录，包含参与者昵称、头像、报名状态和签到时间。
     *
     * <p>不变量：查询过程不修改活动、报名、用户或媒体文件。
     *
     * @param userId 当前调用者 ID
     * @param admin 调用方是否为管理员
     * @param activityId 活动 ID
     * @param page 页码
     * @param pageSize 每页大小
     * @return 活动参与者分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivityParticipant> listParticipants(
            String userId, boolean admin, String activityId, Integer page, Integer pageSize) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity " + activityId + " is not visible"));
        checkParticipantListPermission(userId, admin, activity);

        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        Page<ActivityRegistration> registrationPage =
                activityRegistrationRepository.findByActivityIdOrderByRegisteredAtDesc(
                        activityId, PageRequest.of(resolvedPage - 1, resolvedPageSize));
        Map<String, CommonAvatar> avatarsByUserId = loadParticipantAvatars(registrationPage.getContent());
        List<ActivityDtos.ActivityParticipant> items = registrationPage.getContent().stream()
                .map(registration -> toActivityParticipant(registration, avatarsByUserId.get(registration.getUserId())))
                .toList();

        log.debug(
                "已查询活动参与者列表，activityId={}, userId={}, admin={}, page={}, pageSize={}, total={}",
                sanitizeForLog(activityId),
                sanitizeForLog(userId),
                admin,
                resolvedPage,
                resolvedPageSize,
                registrationPage.getTotalElements());
        return new PageResult<>(
                items,
                registrationPage.getTotalElements(),
                registrationPage.getNumber() + 1,
                registrationPage.getSize(),
                registrationPage.getTotalPages());
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    /**
     * 查询地图上可展示的活动点。
     *
     * <p>前置条件：筛选参数各自独立，任一无效时该筛选条件被忽略。
     *
     * <p>后置条件：返回按距离（若有中心点）或默认顺序排列的活动地图点列表，支持分页。
     *
     * <p>不变量：仅查询 reviewStatus 不为 draft 且 runtimeStatus 不为 takenDown 且有坐标的活动。
     *
     * @param keyword 标题关键词（模糊匹配）
     * @param city 城市筛选
     * @param startAtFrom 开始时间下限
     * @param startAtTo 开始时间上限
     * @param minFee 最低费用
     * @param maxFee 最高费用
     * @param latitude 用户纬度（用于距离排序和筛选）
     * @param longitude 用户经度
     * @param distanceMeters 最大距离（米）
     * @param page 页码
     * @param pageSize 每页大小
     * @return 活动地图点列表
     */
    @Transactional(readOnly = true)
    public List<ActivityDtos.ActivityMapPoint> getMapPoints(
            String keyword,
            String city,
            String startAtFrom,
            String startAtTo,
            Double minFee,
            Double maxFee,
            Double latitude,
            Double longitude,
            Integer distanceMeters,
            Integer page,
            Integer pageSize) {
        List<Activity> activities =
                activityRepository.findByReviewStatusNotAndRuntimeStatusNotAndPointLatIsNotNullAndPointLonIsNotNull(
                        ActivityReviewStatus.draft, ActivityRuntimeStatus.takenDown);

        Instant startAtFromInstant = parseInstantQuietly(startAtFrom);
        Instant startAtToInstant = parseInstantQuietly(startAtTo);

        List<Activity> filtered = activities.stream()
                .filter(activity -> {
                    // 关键词筛选
                    if (keyword != null && !keyword.isBlank()) {
                        if (activity.getTitle() == null || !activity.getTitle().contains(keyword)) {
                            return false;
                        }
                    }
                    // 城市筛选
                    if (city != null && !city.isBlank()) {
                        if (activity.getCity() == null || !activity.getCity().equals(city)) {
                            return false;
                        }
                    }
                    // 时间范围筛选
                    if (startAtFromInstant != null) {
                        if (activity.getStartAt() == null
                                || activity.getStartAt().isBefore(startAtFromInstant)) {
                            return false;
                        }
                    }
                    if (startAtToInstant != null) {
                        if (activity.getStartAt() == null
                                || activity.getStartAt().isAfter(startAtToInstant)) {
                            return false;
                        }
                    }
                    // 费用范围筛选
                    if (minFee != null) {
                        if (activity.getFeeAmount() == null
                                || activity.getFeeAmount().compareTo(BigDecimal.valueOf(minFee)) < 0) {
                            return false;
                        }
                    }
                    if (maxFee != null) {
                        if (activity.getFeeAmount() == null
                                || activity.getFeeAmount().compareTo(BigDecimal.valueOf(maxFee)) > 0) {
                            return false;
                        }
                    }
                    // 距离筛选
                    if (latitude != null && longitude != null && distanceMeters != null) {
                        double dist =
                                haversineDistance(latitude, longitude, activity.getPointLat(), activity.getPointLon());
                        return dist <= distanceMeters;
                    }
                    return true;
                })
                .toList();

        // 距离排序
        if (latitude != null && longitude != null) {
            filtered = filtered.stream()
                    .sorted(Comparator.comparingDouble(activity ->
                            haversineDistance(latitude, longitude, activity.getPointLat(), activity.getPointLon())))
                    .toList();
        }

        // 分页
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int fromIndex = (resolvedPage - 1) * resolvedPageSize;
        if (fromIndex >= filtered.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + resolvedPageSize, filtered.size());

        return filtered.subList(fromIndex, toIndex).stream()
                .map(activityDtoMapper::toActivityMapPoint)
                .toList();
    }

    /**
     * 校验活动可见性。
     *
     * <p>前置条件：activity 非空且已持久化。
     *
     * <p>后置条件：可见时正常返回，不可见时抛出 ErrorCodes.ACTIVITY_NOT_VISIBLE。
     *
     * <p>可见性规则：
     * <ul>
     *   <li>审核通过且未下架：所有人可见</li>
     *   <li>其它状态：仅发起人可见</li>
     * </ul>
     */
    private void checkVisibility(Optional<String> userId, Activity activity) {
        // 审核通过且未下架的活动对所有人可见
        if (activity.getReviewStatus() == ActivityReviewStatus.approved
                && activity.getRuntimeStatus() != ActivityRuntimeStatus.takenDown) {
            return;
        }
        // 其它状态仅发起人可见
        if (userId.isPresent() && userId.get().equals(activity.getOrganizerId())) {
            return;
        }
        throw new BusinessException(ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity {activityId} is not visible");
    }

    /**
     * 加载活动详情（含图片、发起人昵称、审核记录）。
     */
    private ActivityDtos.ActivityDetail loadActivityDetail(Activity activity) {
        List<MediaFile> mediaFiles = activityMediaQueryService.loadMediaFiles(activity.getActivityId());
        Map<UUID, Integer> sortOrderByMediaId = activityMediaQueryService.loadImageSortOrders(activity.getActivityId());
        String organizerName = userRepository
                .findById(activity.getOrganizerId())
                .map(user -> user.getNickname())
                .orElse("未知用户");
        List<ActivityReviewRecord> reviewRecords =
                activityReviewRecordRepository.findByActivityIdOrderByReviewedAtDesc(activity.getActivityId());
        List<ActivityDtos.ReviewRecord> reviewRecordDtos =
                reviewRecords.stream().map(activityDtoMapper::toReviewRecord).toList();
        ActivityRegistrationCounts counts =
                activityRegistrationCountService.countByActivityId(activity.getActivityId());
        ActivityDtos.ActivityDetail detail = activityDtoMapper.toActivityDetail(
                activity,
                organizerName,
                mediaFiles,
                mediaId -> sortOrderByMediaId.getOrDefault(mediaId, Integer.MAX_VALUE),
                reviewRecordDtos,
                counts);
        return detail;
    }

    private ActivityReviewStatus parseReviewStatus(String status) {
        try {
            return ActivityReviewStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "无效的审核状态筛选条件：" + status);
        }
    }

    /**
     * 静默解析时间字符串，解析失败时返回 null。
     */
    private Instant parseInstantQuietly(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            log.debug("地图查询：忽略无效的时间参数 {}", sanitizeForLog(value));
            return null;
        }
    }

    /**
     * Haversine 公式计算两点之间的球面距离（单位：米）。
     *
     * <p>前置条件：所有参数均为有效的经纬度值。
     *
     * <p>后置条件：返回两点间的距离，精确到米。
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusMeters = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }

    private Map<String, ActivityRegistrationCounts> loadCounts(List<Activity> activities) {
        return activityRegistrationCountService.countByActivityIds(
                activities.stream().map(Activity::getActivityId).toList());
    }

    private void checkParticipantListPermission(String userId, boolean admin, Activity activity) {
        if (admin || userId.equals(activity.getOrganizerId())) {
            return;
        }
        if (activity.getReviewStatus() != ActivityReviewStatus.approved
                || activity.getRuntimeStatus() == ActivityRuntimeStatus.takenDown) {
            throw new BusinessException(
                    ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity " + activity.getActivityId() + " is not visible");
        }
        boolean joined = activityRegistrationRepository.existsByActivityIdAndUserIdAndStatusIn(
                activity.getActivityId(),
                userId,
                Set.of(
                        RegistrationStatus.registered,
                        RegistrationStatus.waiting,
                        RegistrationStatus.waitingConfirmation,
                        RegistrationStatus.checkedIn));
        if (!joined) {
            throw new BusinessException(ErrorCodes.ACTIVITY_PERMISSION_DENIED, "No permission to view participants");
        }
    }

    private Map<String, CommonAvatar> loadParticipantAvatars(Collection<ActivityRegistration> registrations) {
        List<String> userIds = registrations.stream()
                .map(ActivityRegistration::getUserId)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, UUID> avatarIdsByUserId = personalProfileRepository.findAllById(userIds).stream()
                .filter(profile -> profile.getAvatarMediaId() != null)
                .collect(Collectors.toMap(PersonalProfile::getUserId, PersonalProfile::getAvatarMediaId));
        merchantProfileRepository.findAllById(userIds).stream()
                .filter(profile -> profile.getAvatarMediaId() != null)
                .forEach(profile -> avatarIdsByUserId.put(profile.getUserId(), profile.getAvatarMediaId()));
        Map<UUID, MediaFile> mediaById = mediaFileRepository.findByMediaIdIn(avatarIdsByUserId.values()).stream()
                .collect(Collectors.toMap(MediaFile::getMediaId, Function.identity()));
        return avatarIdsByUserId.entrySet().stream()
                .filter(entry -> mediaById.containsKey(entry.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new CommonAvatar(mediaAccessService.toSignedDto(mediaById.get(entry.getValue())))));
    }

    private ActivityDtos.ActivityParticipant toActivityParticipant(
            ActivityRegistration registration, CommonAvatar avatar) {
        ActivityDtos.ActivityParticipant result = new ActivityDtos.ActivityParticipant();
        result.setRegistrationId(registration.getRegistrationId());
        result.setUserId(registration.getUserId());
        result.setNickname(
                registration.getUser() == null ? "" : registration.getUser().getNickname());
        result.setAvatar(avatar == null ? null : avatar.value());
        result.setRegistrationStatus(registration.getStatus());
        result.setWaitingRank(registration.getWaitingRank());
        result.setRegisteredAt(registration.getRegisteredAt().toString());
        if (registration.getCheckedInAt() != null) {
            result.setCheckedInAt(registration.getCheckedInAt().toString());
        }
        return result;
    }

    private record CommonAvatar(io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile value) {}
}

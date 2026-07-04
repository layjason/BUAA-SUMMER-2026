package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityImage;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewRecord;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ActivityReviewRecordRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityTemplateRepository;
import io.github.layjason.mayoistar.service.ai.AiContentReviewSnapshotMapper;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import io.github.layjason.mayoistar.service.storage.FileStorageService;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动草稿服务。
 *
 * <p>类职责：管理活动草稿的保存、读取和更新流程，保证草稿仅能由创建者访问，并维护草稿关联图片顺序。
 *
 * <p>类不变量：草稿记录的 organizerId 一旦创建后不被修改；草稿服务操作 reviewStatus=draft 或 changeRequired
 * 的活动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityDraftService {

    private final ActivityRepository activityRepository;
    private final ActivityImageRepository activityImageRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityTemplateRepository activityTemplateRepository;
    private final ActivityReviewRecordRepository activityReviewRecordRepository;
    private final MediaFileRepository mediaFileRepository;
    private final UserRepository userRepository;
    private final ActivityDraftMapper activityDraftMapper;
    private final SubmitActivityValidator submitActivityValidator;
    private final MediaAccessService mediaAccessService;
    private final FileStorageService fileStorageService;
    private final ActivityContentReviewService activityContentReviewService;
    private final AiContentReviewSnapshotMapper aiContentReviewSnapshotMapper;

    /**
     * 保存新的活动草稿。
     *
     * <p>前置条件：调用者用户存在；请求中的时间范围、报名截止时间和地点结构满足草稿规则。
     *
     * <p>后置条件：创建一条 reviewStatus=draft 的活动记录，并返回带图片回显的草稿详情。
     *
     * <p>不变量：新草稿的组织者固定为调用者本人，运行状态初始化为 notStarted。
     *
     * @param organizerId 当前调用者 ID
     * @param request 草稿保存请求
     * @return 新建草稿详情
     */
    @Transactional
    public ActivityDtos.ActivityDraftDetail saveDraft(
            String organizerId, ActivityDtos.ActivityDraftUpsertRequest request) {
        validateUserExists(organizerId);
        CommonDtos.LocationInfo location = request.getLocation();
        validateDraftRequest(request, location);

        Instant now = Instant.now();
        Activity activity = Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title(request.getTitle())
                .tags(copyTags(request.getTags()))
                .introduction(request.getIntroduction())
                .startAt(parseInstant(request.getStartAt(), "活动开始时间"))
                .endAt(parseInstant(request.getEndAt(), "活动结束时间"))
                .pointLon(locationPointLongitude(location))
                .pointLat(locationPointLatitude(location))
                .city(location == null ? null : location.getCity())
                .address(location == null ? null : location.getAddress())
                .placeName(location == null ? null : location.getPlaceName())
                .safetyNotice(request.getSafetyNotice())
                .capacity(request.getCapacity())
                .feeAmount(request.getFeeAmount())
                .feeDescription(request.getFeeDescription())
                .minAge(request.getMinAge())
                .requireLocationCheck(
                        request.getRequireLocationCheck() != null ? request.getRequireLocationCheck() : false)
                .registrationDeadline(parseInstant(request.getRegistrationDeadline(), "报名截止时间"))
                .reviewStatus(ActivityReviewStatus.draft)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .manualReviewRequired(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Activity savedActivity = activityRepository.save(activity);
        replaceImages(organizerId, savedActivity.getActivityId(), request.getImageIds());
        log.info("已保存活动草稿，activityId={}, organizerId={}", savedActivity.getActivityId(), organizerId);
        return loadDraftDetail(savedActivity);
    }

    /**
     * 分页获取当前用户的活动草稿。
     *
     * <p>前置条件：调用者用户存在；分页参数为空时采用默认值。
     *
     * <p>后置条件：仅返回调用者自己创建且处于草稿状态或要求修改状态的活动，按更新时间倒序排列。
     *
     * <p>不变量：不会返回已提交审核、已通过、已驳回的活动。
     *
     * @param organizerId 当前调用者 ID
     * @param page 页码，从 1 开始
     * @param pageSize 每页大小
     * @return 草稿分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivityDraftSummary> listDrafts(
            String organizerId, Integer page, Integer pageSize) {
        validateUserExists(organizerId);
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        PageResult<ActivityDtos.ActivityDraftSummary> result = activityDraftMapper.toDraftSummaryPage(
                activityRepository.findByOrganizerIdAndReviewStatusInOrderByUpdatedAtDesc(
                        organizerId,
                        Set.of(ActivityReviewStatus.draft, ActivityReviewStatus.changeRequired),
                        PageRequest.of(resolvedPage - 1, resolvedPageSize)));
        log.debug(
                "已查询活动草稿列表，organizerId={}, page={}, pageSize={}, total={}",
                organizerId,
                resolvedPage,
                resolvedPageSize,
                result.getTotal());
        return result;
    }

    /**
     * 分页获取活动模板。
     *
     * <p>前置条件：分页参数为空时采用默认值。
     *
     * <p>后置条件：返回活动模板分页结果，默认封面图会转换为可访问的签名媒体 DTO。
     *
     * <p>不变量：不修改模板、媒体文件或活动草稿。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页大小
     * @return 活动模板分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivityTemplate> listTemplates(Integer page, Integer pageSize) {
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        PageResult<ActivityDtos.ActivityTemplate> result = activityDraftMapper.toTemplatePage(
                activityTemplateRepository.findAll(PageRequest.of(resolvedPage - 1, resolvedPageSize)));
        log.debug("已查询活动模板列表，page={}, pageSize={}, total={}", resolvedPage, resolvedPageSize, result.getTotal());
        return result;
    }

    /**
     * 基于活动模板创建草稿。
     *
     * <p>前置条件：调用者用户存在；templateId 对应模板存在。
     *
     * <p>后置条件：创建一条 reviewStatus=draft 的可编辑活动草稿，标题、标签、简介、安全须知和容量来自模板。
     *
     * <p>不变量：模板记录不会被修改；模板封面不自动绑定为草稿图片，避免绕过活动图片的上传者校验。
     *
     * @param organizerId 当前调用者 ID
     * @param templateId 模板 ID
     * @return 新建草稿详情
     */
    @Transactional
    public ActivityDtos.ActivityDraftDetail createDraftFromTemplate(String organizerId, String templateId) {
        validateUserExists(organizerId);
        var template = activityTemplateRepository
                .findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.TEMPLATE_NOT_FOUND, "活动模板不存在"));
        Instant now = Instant.now();
        Instant startAt = now.plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Activity activity = Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title(template.getName())
                .tags(copyTags(template.getDefaultTags()))
                .introduction(template.getDefaultIntroduction())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .safetyNotice(template.getDefaultSafetyNotice())
                .capacity(template.getDefaultCapacity())
                .registrationDeadline(startAt.minus(1, ChronoUnit.DAYS))
                .reviewStatus(ActivityReviewStatus.draft)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .manualReviewRequired(false)
                .requireLocationCheck(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Activity savedActivity = activityRepository.save(activity);
        log.info(
                "已基于活动模板创建草稿，activityId={}, templateId={}, organizerId={}",
                savedActivity.getActivityId(),
                templateId,
                organizerId);
        return loadDraftDetail(savedActivity);
    }

    /**
     * 克隆已有活动为新的活动草稿。
     *
     * <p>前置条件：调用者用户存在；activityId 对应活动存在；调用者是原活动发起人。
     *
     * <p>后置条件：复制原活动基础字段和活动图片副本，生成一条新的 reviewStatus=draft 活动。
     *
     * <p>不变量：原活动、原活动图片、报名记录、签到记录、活动总结和评价记录均不被修改或复制。
     *
     * @param organizerId 当前调用者 ID
     * @param activityId 原活动 ID
     * @return 新建草稿详情
     */
    @Transactional
    public ActivityDtos.ActivityDraftDetail cloneActivity(String organizerId, String activityId) {
        validateUserExists(organizerId);
        Activity source = activityRepository
                .findById(activityId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity {activityId} is not visible"));
        if (!organizerId.equals(source.getOrganizerId())) {
            throw new BusinessException(ErrorCodes.ACTIVITY_PERMISSION_DENIED, "无权克隆其他用户的活动");
        }

        Instant now = Instant.now();
        Activity clone = Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .teamId(source.getTeamId())
                .title(source.getTitle())
                .tags(copyTags(source.getTags()))
                .introduction(source.getIntroduction())
                .startAt(source.getStartAt())
                .endAt(source.getEndAt())
                .pointLon(source.getPointLon())
                .pointLat(source.getPointLat())
                .city(source.getCity())
                .address(source.getAddress())
                .placeName(source.getPlaceName())
                .safetyNotice(source.getSafetyNotice())
                .capacity(source.getCapacity())
                .feeAmount(source.getFeeAmount())
                .feeDescription(source.getFeeDescription())
                .minAge(source.getMinAge())
                .registrationDeadline(source.getRegistrationDeadline())
                .reviewStatus(ActivityReviewStatus.draft)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .manualReviewRequired(false)
                .requireLocationCheck(Boolean.TRUE.equals(source.getRequireLocationCheck()))
                .createdAt(now)
                .updatedAt(now)
                .build();
        Activity savedClone = activityRepository.save(clone);
        cloneImages(organizerId, source.getActivityId(), savedClone.getActivityId());
        log.info(
                "已克隆活动为草稿，sourceActivityId={}, clonedActivityId={}, organizerId={}",
                source.getActivityId(),
                savedClone.getActivityId(),
                organizerId);
        return loadDraftDetail(savedClone);
    }

    /**
     * 查询单个活动草稿详情。
     *
     * <p>前置条件：调用者用户存在；activityId 对应记录存在。
     *
     * <p>后置条件：当草稿属于调用者本人时返回详情，否则抛出 403 或 404。
     *
     * <p>不变量：仅允许读取 reviewStatus=draft 或 changeRequired 的活动。
     *
     * @param organizerId 当前调用者 ID
     * @param activityId 草稿 ID
     * @return 草稿详情
     */
    @Transactional(readOnly = true)
    public ActivityDtos.ActivityDraftDetail getDraft(String organizerId, String activityId) {
        validateUserExists(organizerId);
        return loadDraftDetail(findOwnedDraft(organizerId, activityId));
    }

    /**
     * 更新活动草稿。
     *
     * <p>前置条件：调用者用户存在；草稿存在且属于调用者本人；草稿处于 draft 或 changeRequired 状态。
     *
     * <p>后置条件：草稿信息和图片顺序被新请求覆盖，updatedAt 刷新为当前时间。
     *
     * <p>不变量：organizerId、reviewStatus 与 runtimeStatus 不因草稿更新而改变。
     *
     * @param organizerId 当前调用者 ID
     * @param activityId 草稿 ID
     * @param request 草稿更新请求
     * @return 更新后的草稿详情
     */
    @Transactional
    public ActivityDtos.ActivityDraftDetail updateDraft(
            String organizerId, String activityId, ActivityDtos.ActivityDraftUpsertRequest request) {
        validateUserExists(organizerId);
        Activity activity = findOwnedDraft(organizerId, activityId);
        CommonDtos.LocationInfo location = request.getLocation();
        validateDraftRequest(request, location);

        activity.setTitle(request.getTitle());
        activity.setTags(copyTags(request.getTags()));
        activity.setIntroduction(request.getIntroduction());
        activity.setStartAt(parseInstant(request.getStartAt(), "活动开始时间"));
        activity.setEndAt(parseInstant(request.getEndAt(), "活动结束时间"));
        activity.setPointLon(locationPointLongitude(location));
        activity.setPointLat(locationPointLatitude(location));
        activity.setCity(location == null ? null : location.getCity());
        activity.setAddress(location == null ? null : location.getAddress());
        activity.setPlaceName(location == null ? null : location.getPlaceName());
        activity.setSafetyNotice(request.getSafetyNotice());
        activity.setCapacity(request.getCapacity());
        activity.setFeeAmount(request.getFeeAmount());
        activity.setFeeDescription(request.getFeeDescription());
        activity.setMinAge(request.getMinAge());
        activity.setRequireLocationCheck(
                request.getRequireLocationCheck() != null ? request.getRequireLocationCheck() : false);
        activity.setRegistrationDeadline(parseInstant(request.getRegistrationDeadline(), "报名截止时间"));
        activity.setUpdatedAt(Instant.now());
        Activity savedActivity = activityRepository.save(activity);
        replaceImages(organizerId, savedActivity.getActivityId(), request.getImageIds());
        log.info("已更新活动草稿，activityId={}, organizerId={}", savedActivity.getActivityId(), organizerId);
        return loadDraftDetail(savedActivity);
    }

    /**
     * 提交活动审核。
     *
     * <p>前置条件：调用者用户存在；活动存在且属于调用者本人；活动处于 draft 或 changeRequired 状态；
     * 活动所有必填字段完整且合法。
     *
     * <p>后置条件：活动 reviewStatus 更新为 pending；创建一条审核记录（初始结果为 pending）；
     * AI 低风险且无需人工审核时直接通过；否则标记 manualReviewRequired 并进入 pending。
     *
     * <p>不变量：organizerId 不变；runtimeStatus 不变。
     *
     * @param organizerId 当前调用者 ID
     * @param activityId 活动 ID
     * @return 活动详情
     */
    @Transactional
    public ActivityDtos.ActivityDetail submitActivity(String organizerId, String activityId) {
        validateUserExists(organizerId);
        Activity activity = findActivityForSubmit(organizerId, activityId);
        submitActivityValidator.validateForSubmission(activity);
        List<MediaFile> mediaFiles =
                loadMediaFiles(activityImageRepository.findByActivityIdOrderBySortOrderAsc(activity.getActivityId()));
        AiDtos.AiContentReviewResult aiReview = activityContentReviewService.reviewActivity(activity, mediaFiles);

        boolean manualReviewRequired = shouldManualReview(activity) || requiresManualReview(aiReview);
        ReviewStatus initialReviewResult = manualReviewRequired ? ReviewStatus.pending : ReviewStatus.approved;
        activity.setReviewStatus(manualReviewRequired ? ActivityReviewStatus.pending : ActivityReviewStatus.approved);
        if (!manualReviewRequired) {
            activity.setRuntimeStatus(ActivityRuntimeStatus.notStarted);
        }
        activity.setManualReviewRequired(manualReviewRequired);
        activity.setAiContentReviewJson(aiContentReviewSnapshotMapper.toJson(aiReview));
        activity.setUpdatedAt(Instant.now());
        Activity savedActivity = activityRepository.save(activity);

        ActivityReviewRecord reviewRecord = createInitialReviewRecord(
                savedActivity.getActivityId(), initialReviewResult, buildAiReviewReason(aiReview));
        activityReviewRecordRepository.save(reviewRecord);

        log.info(
                "已提交活动审核，activityId={}, organizerId={}, manualReviewRequired={}",
                savedActivity.getActivityId(),
                organizerId,
                savedActivity.getManualReviewRequired());

        return loadActivityDetail(savedActivity);
    }

    private Activity findActivityForSubmit(String organizerId, String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity {activityId} is not visible"));
        if (!activity.getOrganizerId().equals(organizerId)) {
            throw new BusinessException(ErrorCodes.ACTIVITY_PERMISSION_DENIED, "无权操作其他用户的活动");
        }
        return activity;
    }

    /**
     * 判断是否需要人工审核。
     *
     * <p>前置条件：activity 非空且 capacity 已通过校验。
     *
     * <p>后置条件：capacity >= 50 时返回 true，否则返回 false。
     *
     * <p>不变量：不修改实体。
     *
     * @param activity 活动实体
     * @return 是否需要人工审核
     */
    private boolean shouldManualReview(Activity activity) {
        return activity.getCapacity() != null && activity.getCapacity() >= 50;
    }

    private boolean requiresManualReview(AiDtos.AiContentReviewResult aiReview) {
        return aiReview == null
                || !"succeeded".equals(aiReview.getStatus())
                || aiReview.getSuggestedReviewStatus() != ReviewStatus.approved
                || !"low".equals(aiReview.getRiskLevel());
    }

    /**
     * 创建初始审核记录。
     *
     * <p>前置条件：activityId 非空，result 为本次自动审核后的初始结果。
     *
     * <p>后置条件：返回一条 reviewerId 为空的审核记录。
     *
     * <p>不变量：不修改活动实体，不执行除创建外的其他持久化操作。
     *
     * @param activityId 活动 ID
     * @param result 初始审核结果
     * @param reason AI 审核理由
     * @return 初始审核记录
     */
    private ActivityReviewRecord createInitialReviewRecord(String activityId, ReviewStatus result, String reason) {
        return ActivityReviewRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .activityId(activityId)
                .result(result)
                .reason(reason)
                .reviewedAt(Instant.now())
                .build();
    }

    private String buildAiReviewReason(AiDtos.AiContentReviewResult aiReview) {
        if (aiReview == null) {
            return "AI 内容安全审核未返回结果，转入人工审核";
        }
        String reasons = aiReview.getReasons() == null ? "" : String.join("；", aiReview.getReasons());
        if (aiReview.getFriendlyErrorMessage() != null
                && !aiReview.getFriendlyErrorMessage().isBlank()) {
            return "AI 内容安全审核：" + reasons + "；" + aiReview.getFriendlyErrorMessage();
        }
        return "AI 内容安全审核：" + reasons;
    }

    /**
     * 加载活动详情（含发起人昵称、图片、审核记录）。
     *
     * <p>前置条件：activity 已持久化。
     *
     * <p>后置条件：返回包含完整字段的 ActivityDetail。
     *
     * <p>不变量：不修改传入实体。
     *
     * @param activity 活动实体
     * @return 活动详情 DTO
     */
    private ActivityDtos.ActivityDetail loadActivityDetail(Activity activity) {
        List<ActivityImage> activityImages =
                activityImageRepository.findByActivityIdOrderBySortOrderAsc(activity.getActivityId());
        List<MediaFile> mediaFiles = loadMediaFiles(activityImages);
        Map<UUID, Integer> sortOrderByMediaId = new LinkedHashMap<>();
        for (ActivityImage activityImage : activityImages) {
            sortOrderByMediaId.put(activityImage.getMediaId(), activityImage.getSortOrder());
        }
        String organizerName = userRepository
                .findById(activity.getOrganizerId())
                .map(user -> user.getNickname())
                .orElse("未知用户");
        List<ActivityReviewRecord> reviewRecords =
                activityReviewRecordRepository.findByActivityIdOrderByReviewedAtDesc(activity.getActivityId());
        List<ActivityDtos.ReviewRecord> reviewRecordDtos =
                reviewRecords.stream().map(activityDraftMapper::toReviewRecord).toList();
        return activityDraftMapper.toActivityDetail(
                activity,
                organizerName,
                mediaFiles,
                mediaId -> sortOrderByMediaId.getOrDefault(mediaId, Integer.MAX_VALUE),
                reviewRecordDtos,
                countByStatus(activity.getActivityId(), RegistrationStatus.registered, RegistrationStatus.checkedIn),
                countByStatus(
                        activity.getActivityId(),
                        RegistrationStatus.registered,
                        RegistrationStatus.checkedIn,
                        RegistrationStatus.waitingConfirmation),
                countByStatus(
                        activity.getActivityId(), RegistrationStatus.waiting, RegistrationStatus.waitingConfirmation));
    }

    private Activity findOwnedDraft(String organizerId, String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity {activityId} is not visible"));
        if (!activity.getOrganizerId().equals(organizerId)) {
            throw new BusinessException(ErrorCodes.ACTIVITY_PERMISSION_DENIED, "无权访问其他用户的活动草稿");
        }
        if (activity.getReviewStatus() != ActivityReviewStatus.draft
                && activity.getReviewStatus() != ActivityReviewStatus.changeRequired) {
            throw new BusinessException(ErrorCodes.ACTIVITY_STATE_NOT_SUBMITTABLE, "当前活动不允许按草稿规则访问");
        }
        return activity;
    }

    private ActivityDtos.ActivityDraftDetail loadDraftDetail(Activity activity) {
        List<ActivityImage> activityImages =
                activityImageRepository.findByActivityIdOrderBySortOrderAsc(activity.getActivityId());
        List<MediaFile> mediaFiles = loadMediaFiles(activityImages);
        Map<UUID, Integer> sortOrderByMediaId = new LinkedHashMap<>();
        for (ActivityImage activityImage : activityImages) {
            sortOrderByMediaId.put(activityImage.getMediaId(), activityImage.getSortOrder());
        }
        return activityDraftMapper.toDraftDetail(
                activity, mediaFiles, mediaId -> sortOrderByMediaId.getOrDefault(mediaId, Integer.MAX_VALUE));
    }

    private List<MediaFile> loadMediaFiles(List<ActivityImage> activityImages) {
        if (activityImages.isEmpty()) {
            return List.of();
        }
        List<UUID> mediaIds =
                activityImages.stream().map(ActivityImage::getMediaId).toList();
        Map<UUID, MediaFile> mediaFileMap = mediaFileRepository.findByMediaIdIn(mediaIds).stream()
                .collect(Collectors.toMap(MediaFile::getMediaId, mediaFile -> mediaFile));
        return mediaIds.stream().map(mediaFileMap::get).filter(Objects::nonNull).toList();
    }

    /**
     * 用请求中的图片列表替换活动当前关联的图片，并维护媒体访问策略生命周期。
     *
     * <p>前置条件：{@code organizerId} 为活动组织者；imageIds 中的媒体均由组织者上传且用途为 activityImage。
     *
     * <p>后置条件：activity_images 关联表被新列表覆盖；新绑定的图片访问策略升级为
     * {@code activityOwner}（scope=activityId，仅组织者可见）；从草稿移除的图片被软删除，
     * 其旧签名 URL 因 accessVersion 递增而立即失效。
     *
     * <p>不变量：仅处理调用者本人拥有的 activityImage 媒体；重复绑定同一图片因幂等护栏不产生额外版本递增。
     *
     * @param organizerId 活动组织者（同时为图片上传者）
     * @param activityId  活动 ID
     * @param imageIds    新的图片媒体 ID 列表，可为空表示清空
     */
    private void replaceImages(String organizerId, String activityId, Collection<UUID> imageIds) {
        List<UUID> previousMediaIds = activityImageRepository.findByActivityIdOrderBySortOrderAsc(activityId).stream()
                .map(ActivityImage::getMediaId)
                .toList();
        activityImageRepository.deleteByActivityId(activityId);

        List<UUID> newMediaIds = imageIds == null ? List.of() : List.copyOf(imageIds);
        if (!newMediaIds.isEmpty()) {
            validateMediaFiles(organizerId, newMediaIds);
            int index = 0;
            for (UUID imageId : newMediaIds) {
                activityImageRepository.save(ActivityImage.builder()
                        .imageId(UUID.randomUUID().toString())
                        .activityId(activityId)
                        .mediaId(imageId)
                        .sortOrder(index++)
                        .build());
                // 绑定草稿后升级为 activityOwner，仅活动组织者可见（幂等，重复绑定跳过）
                mediaAccessService.updateAccessPolicy(
                        imageId, MediaAccessPolicy.activityOwner, activityId, organizerId);
            }
        }

        // 从草稿移除的图片软删除，回收存储并使旧签名 URL 立即失效
        Set<UUID> retained = new HashSet<>(newMediaIds);
        for (UUID removed : previousMediaIds) {
            if (!retained.contains(removed)) {
                mediaAccessService.softDelete(removed);
            }
        }
    }

    /**
     * 复制原活动图片为新草稿专属媒体，避免草稿编辑影响原活动图片。
     *
     * <p>前置条件：sourceActivityId 和 targetActivityId 均属于 organizerId 创建的活动。
     *
     * <p>后置条件：为目标草稿创建新的 activityImage 媒体记录和 activity_images 关联。
     *
     * <p>不变量：不复用原媒体 ID，不修改原媒体访问策略或删除状态。
     *
     * @param organizerId 活动组织者
     * @param sourceActivityId 原活动 ID
     * @param targetActivityId 目标草稿 ID
     */
    private void cloneImages(String organizerId, String sourceActivityId, String targetActivityId) {
        List<ActivityImage> sourceImages =
                activityImageRepository.findByActivityIdOrderBySortOrderAsc(sourceActivityId);
        if (sourceImages.isEmpty()) {
            return;
        }
        List<MediaFile> sourceMediaFiles = loadMediaFiles(sourceImages);
        Map<UUID, MediaFile> mediaById =
                sourceMediaFiles.stream().collect(Collectors.toMap(MediaFile::getMediaId, mediaFile -> mediaFile));
        for (ActivityImage sourceImage : sourceImages) {
            MediaFile sourceMedia = mediaById.get(sourceImage.getMediaId());
            if (sourceMedia == null || sourceMedia.getDeletedAt() != null) {
                continue;
            }
            MediaFile clonedMedia = cloneMediaFile(organizerId, targetActivityId, sourceMedia);
            activityImageRepository.save(ActivityImage.builder()
                    .imageId(UUID.randomUUID().toString())
                    .activityId(targetActivityId)
                    .mediaId(clonedMedia.getMediaId())
                    .sortOrder(sourceImage.getSortOrder())
                    .build());
        }
    }

    private MediaFile cloneMediaFile(String organizerId, String targetActivityId, MediaFile sourceMedia) {
        UUID clonedMediaId = UUID.randomUUID();
        String clonedKey = MediaUsage.activityImage.name() + "/" + organizerId + "/" + clonedMediaId + "_"
                + sourceMedia.getFileName();
        try (InputStream inputStream = fileStorageService.retrieve(sourceMedia.getStoragePath())) {
            fileStorageService.store(clonedKey, inputStream, sourceMedia.getContentType(), sourceMedia.getSizeBytes());
        } catch (Exception exception) {
            log.warn(
                    "克隆活动图片失败，sourceMediaId={}, targetActivityId={}",
                    sourceMedia.getMediaId(),
                    targetActivityId,
                    exception);
            throw new BusinessException(ErrorCodes.MEDIA_FILE_UNAVAILABLE, "活动图片复制失败");
        }
        return mediaFileRepository.save(MediaFile.builder()
                .mediaId(clonedMediaId)
                .fileName(sourceMedia.getFileName())
                .contentType(sourceMedia.getContentType())
                .sizeBytes(sourceMedia.getSizeBytes())
                .usage(MediaUsage.activityImage)
                .storagePath(clonedKey)
                .visibility(MediaVisibility.privateVisible)
                .accessPolicy(MediaAccessPolicy.activityOwner)
                .accessScopeId(targetActivityId)
                .accessVersion(1L)
                .uploadedBy(organizerId)
                .uploadedAt(Instant.now())
                .build());
    }

    /**
     * 校验用于活动的媒体文件均可用：存在、未软删除、用途为 activityImage 且由组织者本人上传。
     *
     * <p>前置条件：mediaIds 非空。
     *
     * <p>后置条件：全部满足要求时正常返回；任一不满足则抛出 MEDIA_FILE_UNAVAILABLE。
     *
     * @param organizerId 活动组织者（校验图片归属）
     * @param mediaIds    待校验的媒体 ID 集合
     */
    private void validateMediaFiles(String organizerId, Collection<UUID> mediaIds) {
        List<MediaFile> mediaFiles = mediaFileRepository.findByMediaIdIn(mediaIds);
        if (mediaFiles.size() != mediaIds.size()) {
            throw new BusinessException(ErrorCodes.MEDIA_FILE_UNAVAILABLE, "存在不可用的活动图片");
        }
        for (MediaFile mediaFile : mediaFiles) {
            if (mediaFile.getDeletedAt() != null
                    || mediaFile.getUsage() != MediaUsage.activityImage
                    || !organizerId.equals(mediaFile.getUploadedBy())) {
                throw new BusinessException(ErrorCodes.MEDIA_FILE_UNAVAILABLE, "存在不可用的活动图片");
            }
        }
    }

    private void validateUserExists(String organizerId) {
        if (!userRepository.existsById(organizerId)) {
            throw new BusinessException(400, "当前调用者不存在");
        }
    }

    private void validateDraftRequest(
            ActivityDtos.ActivityDraftUpsertRequest request, CommonDtos.LocationInfo location) {
        Instant startAt = parseInstant(request.getStartAt(), "活动开始时间");
        Instant endAt = parseInstant(request.getEndAt(), "活动结束时间");
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动结束时间必须晚于开始时间");
        }
        Instant registrationDeadline = parseInstant(request.getRegistrationDeadline(), "报名截止时间");
        if (registrationDeadline != null && startAt != null && registrationDeadline.isAfter(startAt)) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "报名截止时间不能晚于活动开始时间");
        }
        if (request.getCapacity() != null && request.getCapacity() < 1) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动人数上限必须大于 0");
        }
        if (request.getMinAge() != null && request.getMinAge() < 0) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "最小年龄不能为负数");
        }
        if (location != null && location.getPoint() != null) {
            Double longitude = location.getPoint().getLongitude();
            Double latitude = location.getPoint().getLatitude();
            if (longitude == null || latitude == null) {
                throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动地点坐标必须同时提供经纬度");
            }
        }
    }

    private Instant parseInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, fieldName + "格式不合法");
        }
    }

    private List<String> copyTags(List<String> tags) {
        return tags == null ? null : List.copyOf(tags);
    }

    private Double locationPointLongitude(CommonDtos.LocationInfo location) {
        return location == null || location.getPoint() == null
                ? null
                : location.getPoint().getLongitude();
    }

    private Double locationPointLatitude(CommonDtos.LocationInfo location) {
        return location == null || location.getPoint() == null
                ? null
                : location.getPoint().getLatitude();
    }

    /**
     * 统计指定活动在给定状态集合中的报名记录数。
     */
    private int countByStatus(String activityId, RegistrationStatus... statuses) {
        return activityRegistrationRepository
                .findByActivityIdAndStatusIn(activityId, Set.of(statuses))
                .size();
    }
}

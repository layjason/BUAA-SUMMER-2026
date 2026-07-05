package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.config.ActivityProperties;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.ActivityReview;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.ActivitySummaryImage;
import io.github.layjason.mayoistar.entity.activities.ActivitySummaryPost;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityReviewRepository;
import io.github.layjason.mayoistar.repository.activities.ActivitySummaryImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivitySummaryPostRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动总结与评价业务服务。
 *
 * <p>类职责：处理活动结束后的图文总结发布与参与者评价提交。
 *
 * <p>类不变量：仅审核通过且未下架的活动可发布总结或评价；总结由发起人发布且每个活动最多一篇；
 * 评价由已签到参与者提交且每人每活动最多一条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivitySummaryReviewService {

    private static final Set<MediaUsage> SUMMARY_IMAGE_USAGES =
            Set.of(MediaUsage.activityImage, MediaUsage.summaryImage);

    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivitySummaryPostRepository activitySummaryPostRepository;
    private final ActivitySummaryImageRepository activitySummaryImageRepository;
    private final ActivityReviewRepository activityReviewRepository;
    private final MediaFileRepository mediaFileRepository;
    private final UserRepository userRepository;
    private final MediaAccessService mediaAccessService;
    private final ActivityProperties activityProperties;

    /**
     * 发布活动图文总结。
     *
     * <p>前置条件：activityId 对应审核通过且已结束的活动；userId 为活动发起人；
     * request 中每张图片均存在、未删除、由当前用户上传，并且已包含人工确认标签。
     *
     * <p>后置条件：创建一条 ActivitySummaryPost 与对应 ActivitySummaryImage 记录，并返回带签名图片地址的总结 DTO。
     *
     * <p>不变量：同一活动最多存在一篇总结；活动、媒体文件本身不被修改。
     *
     * @param userId     当前用户 ID
     * @param activityId 活动 ID
     * @param request    总结请求
     * @return 活动总结响应
     */
    @Transactional
    public ActivityDtos.ActivitySummaryPost createSummary(
            String userId, String activityId, ActivityDtos.ActivitySummaryPostRequest request) {
        Activity activity = loadVisibleActivity(activityId);
        if (!Objects.equals(activity.getOrganizerId(), userId)) {
            throw new BusinessException(
                    ErrorCodes.ACTIVITY_PERMISSION_DENIED, "Permission is denied for this activity");
        }
        assertActivityEnded(activity);
        if (activitySummaryPostRepository.existsByActivityId(activityId)) {
            throw new BusinessException(ErrorCodes.DUPLICATE_SUMMARY, "Activity summary already exists");
        }

        Map<UUID, CommonDtos.ImageTagConfirmation> tagsByMediaId = normalizeConfirmedTags(request);
        List<MediaFile> mediaFiles = loadSummaryImages(userId, request.getImageIds(), tagsByMediaId);
        Instant now = Instant.now();
        ActivitySummaryPost summaryPost = ActivitySummaryPost.builder()
                .summaryId(UUID.randomUUID().toString())
                .activityId(activityId)
                .userId(userId)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .createdAt(now)
                .build();
        activitySummaryPostRepository.save(summaryPost);

        List<ActivitySummaryImage> summaryImages = mediaFiles.stream()
                .map(mediaFile -> ActivitySummaryImage.builder()
                        .imageId(UUID.randomUUID().toString())
                        .summaryId(summaryPost.getSummaryId())
                        .mediaId(mediaFile.getMediaId())
                        .tags(copyTags(tagsByMediaId.get(mediaFile.getMediaId()).getTags()))
                        .build())
                .toList();
        activitySummaryImageRepository.saveAll(summaryImages);

        log.info(
                "活动总结已发布: activityId={}, summaryId={}, userId={}, imageCount={}",
                activityId,
                summaryPost.getSummaryId(),
                userId,
                summaryImages.size());
        return toSummaryDto(summaryPost, mediaFiles, summaryImages);
    }

    /**
     * 提交活动评价。
     *
     * <p>前置条件：activityId 对应审核通过且已结束的活动；userId 对应报名记录存在且状态为 checkedIn；
     * 当前用户尚未评价该活动。
     *
     * <p>后置条件：创建一条 ActivityReview 记录，并返回评价 DTO。
     *
     * <p>不变量：同一用户对同一活动最多存在一条评价；活动和报名记录不被修改。
     *
     * @param userId     当前用户 ID
     * @param activityId 活动 ID
     * @param request    评价请求
     * @return 活动评价响应
     */
    @Transactional
    public ActivityDtos.ActivityReview createReview(
            String userId, String activityId, ActivityDtos.ActivityReviewRequest request) {
        Activity activity = loadVisibleActivity(activityId);
        assertActivityEnded(activity);
        assertReviewWindowOpen(activity);
        ActivityRegistration registration = activityRegistrationRepository
                .findByActivityIdAndUserId(activityId, userId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCodes.REGISTRATION_NOT_FOUND, "Registration does not exist"));
        if (registration.getStatus() != RegistrationStatus.checkedIn) {
            throw new BusinessException(ErrorCodes.REGISTRATION_NOT_FOUND, "Registration does not exist");
        }
        if (activityReviewRepository.existsByActivityIdAndUserId(activityId, userId)) {
            throw new BusinessException(ErrorCodes.DUPLICATE_REVIEW, "Activity review already exists");
        }

        ActivityReview review = ActivityReview.builder()
                .reviewId(UUID.randomUUID().toString())
                .activityId(activityId)
                .userId(userId)
                .rating(request.getRating())
                .content(trimToNull(request.getContent()))
                .tags(request.getTags() == null ? List.of() : List.copyOf(request.getTags()))
                .createdAt(Instant.now())
                .build();
        activityReviewRepository.save(review);

        log.info("活动评价已提交: activityId={}, reviewId={}, userId={}", activityId, review.getReviewId(), userId);
        return toReviewDto(review);
    }

    /**
     * 分页查询活动图文总结。
     *
     * <p>前置条件：activityId 对应活动存在且对调用方可见。
     *
     * <p>后置条件：返回按发布时间倒序排列的活动总结分页数据，包含总结图片与标签。
     *
     * <p>不变量：查询过程不修改活动、总结或媒体文件。
     *
     * @param activityId 活动 ID
     * @param page       页码，从 1 开始
     * @param pageSize   每页条数
     * @return 活动总结分页
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivitySummaryPost> listSummaries(
            String activityId, Integer page, Integer pageSize) {
        loadVisibleActivity(activityId);
        Page<ActivitySummaryPost> summaryPage = activitySummaryPostRepository.findByActivityId(
                activityId, pageRequest(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<String, List<ActivitySummaryImage>> imagesBySummaryId =
                loadSummaryImagesBySummaryId(summaryPage.getContent());
        List<ActivityDtos.ActivitySummaryPost> items = summaryPage.getContent().stream()
                .map(summary ->
                        toSummaryDto(summary, imagesBySummaryId.getOrDefault(summary.getSummaryId(), List.of())))
                .toList();
        return toPageResult(summaryPage, items);
    }

    /**
     * 查询当前用户发布的活动总结。
     *
     * <p>前置条件：activityId 对应活动存在且对调用方可见；userId 为当前登录用户。
     *
     * <p>后置条件：若当前用户已发布总结则返回 summary，否则 summary 为 null。
     *
     * <p>不变量：查询过程不修改活动、总结或媒体文件。
     *
     * @param userId     当前用户 ID
     * @param activityId 活动 ID
     * @return 当前用户总结查询结果
     */
    @Transactional(readOnly = true)
    public ActivityDtos.MyActivitySummaryResult getMySummary(String userId, String activityId) {
        loadVisibleActivity(activityId);
        ActivityDtos.MyActivitySummaryResult result = new ActivityDtos.MyActivitySummaryResult();
        activitySummaryPostRepository
                .findByActivityIdAndUserId(activityId, userId)
                .ifPresent(summary -> {
                    List<ActivitySummaryImage> images =
                            activitySummaryImageRepository.findBySummaryIdIn(List.of(summary.getSummaryId()));
                    result.setSummary(toSummaryDto(summary, images));
                });
        return result;
    }

    /**
     * 分页查询活动评价。
     *
     * <p>前置条件：activityId 对应活动存在且对调用方可见。
     *
     * <p>后置条件：返回按创建时间倒序排列的评价分页数据，并补充评价用户昵称。
     *
     * <p>不变量：查询过程不修改活动、评价或用户信息。
     *
     * @param activityId 活动 ID
     * @param page       页码，从 1 开始
     * @param pageSize   每页条数
     * @return 活动评价分页
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivityReviewListItem> listReviews(
            String activityId, Integer page, Integer pageSize) {
        loadVisibleActivity(activityId);
        Page<ActivityReview> reviewPage = activityReviewRepository.findByActivityId(
                activityId, pageRequest(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<String, String> nicknamesByUserId =
                userRepository
                        .findAllById(reviewPage.getContent().stream()
                                .map(ActivityReview::getUserId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(user -> user.getUserId(), user -> user.getNickname()));
        List<ActivityDtos.ActivityReviewListItem> items = reviewPage.getContent().stream()
                .map(review -> toReviewListItemDto(review, nicknamesByUserId.getOrDefault(review.getUserId(), "")))
                .toList();
        return toPageResult(reviewPage, items);
    }

    /**
     * 查询当前用户提交的活动评价。
     *
     * <p>前置条件：activityId 对应活动存在且对调用方可见；userId 为当前登录用户。
     *
     * <p>后置条件：若当前用户已评价则返回 review，否则 review 为 null。
     *
     * <p>不变量：查询过程不修改活动或评价记录。
     *
     * @param userId     当前用户 ID
     * @param activityId 活动 ID
     * @return 当前用户评价查询结果
     */
    @Transactional(readOnly = true)
    public ActivityDtos.MyActivityReviewResult getMyReview(String userId, String activityId) {
        loadVisibleActivity(activityId);
        ActivityDtos.MyActivityReviewResult result = new ActivityDtos.MyActivityReviewResult();
        activityReviewRepository
                .findByActivityIdAndUserId(activityId, userId)
                .ifPresent(review -> result.setReview(toReviewDto(review)));
        return result;
    }

    private Activity loadVisibleActivity(String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity " + activityId + " is not visible"));
        if (activity.getReviewStatus() != ActivityReviewStatus.approved
                || activity.getRuntimeStatus() == ActivityRuntimeStatus.takenDown) {
            throw new BusinessException(ErrorCodes.ACTIVITY_NOT_VISIBLE, "Activity " + activityId + " is not visible");
        }
        return activity;
    }

    private void assertActivityEnded(Activity activity) {
        boolean endedByRuntime = activity.getRuntimeStatus() == ActivityRuntimeStatus.ended;
        boolean endedByTime =
                activity.getEndAt() != null && !activity.getEndAt().isAfter(Instant.now());
        if (!endedByRuntime && !endedByTime) {
            throw new BusinessException(ErrorCodes.ACTIVITY_NOT_ENDED, "Activity has not ended");
        }
    }

    /**
     * 校验活动评价窗口是否仍然开放。
     *
     * <p>前置条件：activity 审核通过且未下架。
     *
     * <p>后置条件：若评价窗口已过期，抛出 REGISTRATION_NOT_FOUND 异常。
     *
     * <p>不变量：活动状态不被修改。
     *
     * @param activity 活动实体
     */
    private void assertReviewWindowOpen(Activity activity) {
        if (activity.getEndAt() == null) {
            return;
        }
        Instant reviewWindowEnds = activity.getEndAt().plus(Duration.ofDays(activityProperties.getReviewWindowDays()));
        if (!Instant.now().isBefore(reviewWindowEnds)) {
            throw new BusinessException(
                    ErrorCodes.REGISTRATION_NOT_FOUND,
                    "Review window has expired for activity " + activity.getActivityId());
        }
    }

    private Map<UUID, CommonDtos.ImageTagConfirmation> normalizeConfirmedTags(
            ActivityDtos.ActivitySummaryPostRequest request) {
        Map<UUID, CommonDtos.ImageTagConfirmation> tagsByMediaId = new LinkedHashMap<>();
        for (CommonDtos.ImageTagConfirmation confirmation : request.getConfirmedImageTags()) {
            if (confirmation.getMediaId() == null) {
                throw new BusinessException(ErrorCodes.MEDIA_FILE_UNAVAILABLE, "Media file is unavailable");
            }
            tagsByMediaId.put(confirmation.getMediaId(), confirmation);
        }
        for (UUID imageId : request.getImageIds()) {
            if (!tagsByMediaId.containsKey(imageId)) {
                throw new BusinessException(ErrorCodes.MEDIA_FILE_UNAVAILABLE, "Media file is unavailable");
            }
        }
        return tagsByMediaId;
    }

    private List<MediaFile> loadSummaryImages(
            String userId, List<UUID> imageIds, Map<UUID, CommonDtos.ImageTagConfirmation> tagsByMediaId) {
        List<UUID> distinctImageIds = imageIds.stream().distinct().toList();
        if (distinctImageIds.size() != imageIds.size()) {
            throw new BusinessException(ErrorCodes.MEDIA_FILE_UNAVAILABLE, "Media file is unavailable");
        }
        Map<UUID, MediaFile> mediaById = mediaFileRepository.findByMediaIdIn(imageIds).stream()
                .collect(Collectors.toMap(MediaFile::getMediaId, Function.identity()));
        List<MediaFile> mediaFiles = new ArrayList<>();
        for (UUID imageId : imageIds) {
            MediaFile mediaFile = mediaById.get(imageId);
            if (mediaFile == null
                    || mediaFile.getDeletedAt() != null
                    || !Objects.equals(mediaFile.getUploadedBy(), userId)
                    || !SUMMARY_IMAGE_USAGES.contains(mediaFile.getUsage())
                    || !tagsByMediaId.containsKey(imageId)) {
                throw new BusinessException(ErrorCodes.MEDIA_FILE_UNAVAILABLE, "Media file is unavailable");
            }
            mediaFiles.add(mediaFile);
        }
        return mediaFiles;
    }

    private ActivityDtos.ActivitySummaryPost toSummaryDto(
            ActivitySummaryPost summaryPost, List<MediaFile> mediaFiles, List<ActivitySummaryImage> summaryImages) {
        Map<UUID, ActivitySummaryImage> imageByMediaId =
                summaryImages.stream().collect(Collectors.toMap(ActivitySummaryImage::getMediaId, Function.identity()));
        ActivityDtos.ActivitySummaryPost dto = new ActivityDtos.ActivitySummaryPost();
        dto.setSummaryId(summaryPost.getSummaryId());
        dto.setActivityId(summaryPost.getActivityId());
        dto.setTitle(summaryPost.getTitle());
        dto.setContent(summaryPost.getContent());
        dto.setImages(mediaFiles.stream().map(mediaAccessService::toSignedDto).toList());
        dto.setImageTags(mediaFiles.stream()
                .map(mediaFile -> toImageTagConfirmation(imageByMediaId.get(mediaFile.getMediaId())))
                .toList());
        dto.setCreatedAt(summaryPost.getCreatedAt().toString());
        return dto;
    }

    private ActivityDtos.ActivitySummaryPost toSummaryDto(
            ActivitySummaryPost summaryPost, List<ActivitySummaryImage> summaryImages) {
        List<UUID> mediaIds =
                summaryImages.stream().map(ActivitySummaryImage::getMediaId).toList();
        Map<UUID, MediaFile> mediaById = mediaFileRepository.findByMediaIdIn(mediaIds).stream()
                .collect(Collectors.toMap(MediaFile::getMediaId, Function.identity()));
        List<MediaFile> mediaFiles = summaryImages.stream()
                .map(summaryImage -> mediaById.get(summaryImage.getMediaId()))
                .filter(Objects::nonNull)
                .toList();
        return toSummaryDto(summaryPost, mediaFiles, summaryImages);
    }

    private CommonDtos.ImageTagConfirmation toImageTagConfirmation(ActivitySummaryImage summaryImage) {
        CommonDtos.ImageTagConfirmation dto = new CommonDtos.ImageTagConfirmation();
        dto.setMediaId(summaryImage.getMediaId());
        dto.setTags(summaryImage.getTags() == null ? List.of() : List.copyOf(summaryImage.getTags()));
        return dto;
    }

    private List<String> copyTags(List<String> tags) {
        return tags == null ? List.of() : List.copyOf(tags);
    }

    private ActivityDtos.ActivityReview toReviewDto(ActivityReview review) {
        ActivityDtos.ActivityReview dto = new ActivityDtos.ActivityReview();
        dto.setReviewId(review.getReviewId());
        dto.setActivityId(review.getActivityId());
        dto.setUserId(review.getUserId());
        dto.setRating(review.getRating());
        dto.setContent(review.getContent());
        dto.setTags(review.getTags() == null ? List.of() : List.copyOf(review.getTags()));
        dto.setCreatedAt(review.getCreatedAt().toString());
        return dto;
    }

    private ActivityDtos.ActivityReviewListItem toReviewListItemDto(ActivityReview review, String nickname) {
        ActivityDtos.ActivityReviewListItem dto = new ActivityDtos.ActivityReviewListItem();
        dto.setReviewId(review.getReviewId());
        dto.setActivityId(review.getActivityId());
        dto.setUserId(review.getUserId());
        dto.setRating(review.getRating());
        dto.setContent(review.getContent());
        dto.setTags(review.getTags() == null ? List.of() : List.copyOf(review.getTags()));
        dto.setCreatedAt(review.getCreatedAt().toString());
        dto.setNickname(nickname);
        return dto;
    }

    private Map<String, List<ActivitySummaryImage>> loadSummaryImagesBySummaryId(List<ActivitySummaryPost> summaries) {
        List<String> summaryIds =
                summaries.stream().map(ActivitySummaryPost::getSummaryId).toList();
        if (summaryIds.isEmpty()) {
            return Map.of();
        }
        return activitySummaryImageRepository.findBySummaryIdIn(summaryIds).stream()
                .collect(Collectors.groupingBy(ActivitySummaryImage::getSummaryId));
    }

    private PageRequest pageRequest(Integer page, Integer pageSize, Sort sort) {
        int pageNumber = page != null && page > 0 ? page : 1;
        int size = pageSize != null && pageSize > 0 ? Math.min(pageSize, 100) : 20;
        return PageRequest.of(pageNumber - 1, size, sort);
    }

    private <Item> PageResult<Item> toPageResult(Page<?> page, List<Item> items) {
        return new PageResult<>(
                items, page.getTotalElements(), page.getNumber() + 1, page.getSize(), page.getTotalPages());
    }

    private String trimToNull(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }
}

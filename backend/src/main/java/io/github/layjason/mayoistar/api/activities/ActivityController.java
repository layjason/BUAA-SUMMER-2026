package io.github.layjason.mayoistar.api.activities;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.service.activities.ActivityDraftService;
import io.github.layjason.mayoistar.service.activities.ActivityQueryService;
import io.github.layjason.mayoistar.service.activities.ActivitySearchCriteria;
import io.github.layjason.mayoistar.service.activities.ActivitySearchService;
import io.github.layjason.mayoistar.service.activities.RequestActorResolver;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/activities")
public class ActivityController {

    private final DefaultApiResponseFactory responseFactory;
    private final ActivityDraftService activityDraftService;
    private final ActivityQueryService activityQueryService;
    private final ActivitySearchService activitySearchService;
    private final RequestActorResolver requestActorResolver;

    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> saveDraft(
            @Valid @RequestBody ActivityDtos.ActivityDraftUpsertRequest request) {
        return requestActorResolver
                .resolveCurrentUserId()
                .map(userId -> ResponseEntity.ok(ApiResponse.success(activityDraftService.saveDraft(userId, request))))
                .orElseGet(responseFactory::activityDraftDetail);
    }

    @GetMapping("/drafts")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivityDraftSummary>>> listDrafts(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return requestActorResolver
                .resolveCurrentUserId()
                .map(userId ->
                        ResponseEntity.ok(ApiResponse.success(activityDraftService.listDrafts(userId, page, pageSize))))
                .orElseGet(responseFactory::emptyPage);
    }

    @GetMapping("/drafts/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> getDraft(@PathVariable String activityId) {
        return requestActorResolver
                .resolveCurrentUserId()
                .map(userId ->
                        ResponseEntity.ok(ApiResponse.success(activityDraftService.getDraft(userId, activityId))))
                .orElseGet(responseFactory::activityDraftDetail);
    }

    @PatchMapping("/drafts/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> updateDraft(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivityDraftUpsertRequest request) {
        return requestActorResolver
                .resolveCurrentUserId()
                .map(userId -> ResponseEntity.ok(
                        ApiResponse.success(activityDraftService.updateDraft(userId, activityId, request))))
                .orElseGet(responseFactory::activityDraftDetail);
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummary>>> getFeed(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/map")
    public ResponseEntity<ApiResponse<List<ActivityDtos.ActivityMapPoint>>> getMapPoints(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> activityTypes,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String startAtFrom,
            @RequestParam(required = false) String startAtTo,
            @RequestParam(required = false) Double minFee,
            @RequestParam(required = false) Double maxFee,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer distanceMeters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        ActivitySearchCriteria criteria = searchCriteria(
                keyword,
                activityTypes,
                city,
                startAtFrom,
                startAtTo,
                minFee,
                maxFee,
                latitude,
                longitude,
                distanceMeters,
                page,
                pageSize);
        return ResponseEntity.ok(ApiResponse.success(activitySearchService.mapPoints(criteria)));
    }

    @PostMapping(value = "/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile>>
            uploadActivityImage(@RequestPart(value = "file") MultipartFile file) {
        return responseFactory.mediaFile(MediaUsage.activityImage);
    }

    @PostMapping(value = "/media/review-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile>>
            uploadActivityReviewImage(@RequestPart(value = "file") MultipartFile file) {
        return responseFactory.mediaFile(MediaUsage.activityReviewImage);
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummary>>> listMyActivities(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return requestActorResolver
                .resolveCurrentUserId()
                .map(userId -> ResponseEntity.ok(
                        ApiResponse.success(activityQueryService.listMyActivities(userId, status, page, pageSize))))
                .orElseGet(responseFactory::emptyPage);
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummary>>> searchActivities(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> activityTypes,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String startAtFrom,
            @RequestParam(required = false) String startAtTo,
            @RequestParam(required = false) Double minFee,
            @RequestParam(required = false) Double maxFee,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer distanceMeters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        ActivitySearchCriteria criteria = searchCriteria(
                keyword,
                activityTypes,
                city,
                startAtFrom,
                startAtTo,
                minFee,
                maxFee,
                latitude,
                longitude,
                distanceMeters,
                page,
                pageSize);
        return ResponseEntity.ok(ApiResponse.success(activitySearchService.search(criteria)));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivityTemplate>>> listTemplates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @PostMapping("/templates/{templateId}/drafts")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> createDraftFromTemplate(
            @PathVariable String templateId) {
        return responseFactory.activityDraftDetail();
    }

    @GetMapping("/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> getActivity(@PathVariable String activityId) {
        Optional<String> userId = requestActorResolver.resolveCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activityQueryService.getActivity(userId, activityId)));
    }

    @PostMapping("/{activityId}/check-in-qrcode")
    public ResponseEntity<ApiResponse<ActivityDtos.CheckInQrCode>> createCheckInQrCode(
            @PathVariable String activityId) {
        return responseFactory.checkInQrCode();
    }

    @PostMapping("/{activityId}/check-ins")
    public ResponseEntity<ApiResponse<ActivityDtos.CheckInRecord>> checkIn(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.CheckInRequest request) {
        return responseFactory.checkInRecord();
    }

    @GetMapping("/{activityId}/check-ins")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.CheckInRecord>>> listCheckIns(
            @PathVariable String activityId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping(value = "/{activityId}/check-ins/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> exportCheckIns(@PathVariable String activityId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new byte[] {0});
    }

    @PostMapping("/{activityId}/clone")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> cloneActivity(
            @PathVariable String activityId) {
        return responseFactory.activityDraftDetail();
    }

    @GetMapping("/{activityId}/participants")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivityParticipant>>> listParticipants(
            @PathVariable String activityId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/{activityId}/participation-state")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityParticipationState>> getParticipationState(
            @PathVariable String activityId) {
        return responseFactory.participationState();
    }

    @PostMapping("/{activityId}/registrations")
    public ResponseEntity<ApiResponse<ActivityDtos.RegistrationResult>> registerActivity(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.RegisterActivityRequest request) {
        return responseFactory.registrationResult();
    }

    @PostMapping("/{activityId}/registrations/cancel")
    public ResponseEntity<ApiResponse<ActivityDtos.RegistrationResult>> cancelRegistration(
            @PathVariable String activityId) {
        return responseFactory.registrationResult();
    }

    @PostMapping("/{activityId}/reviews")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityReview>> reviewActivity(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivityReviewRequest request) {
        return responseFactory.activityReview();
    }

    @PostMapping("/{activityId}/submit")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> submitActivity(@PathVariable String activityId) {
        return requestActorResolver
                .resolveCurrentUserId()
                .map(userId ->
                        ResponseEntity.ok(ApiResponse.success(activityDraftService.submitActivity(userId, activityId))))
                .orElseGet(responseFactory::activityDetail);
    }

    @PostMapping("/{activityId}/summaries")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivitySummaryPost>> createSummary(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivitySummaryPostRequest request) {
        return responseFactory.activitySummaryPost();
    }

    @PostMapping("/{activityId}/waiting-confirmations")
    public ResponseEntity<ApiResponse<ActivityDtos.RegistrationResult>> confirmWaitingSeat(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.WaitingConfirmationRequest request) {
        return responseFactory.registrationResult();
    }

    /**
     * 构造活动搜索条件。
     *
     * <p>前置条件：Controller 已完成 query 参数绑定，时间字符串若存在应符合 ISO-8601 格式。
     *
     * <p>后置条件：返回服务层统一使用的搜索条件对象。
     *
     * <p>不变量：费用字段仅转为筛选值，不创建支付或订单语义。
     */
    private ActivitySearchCriteria searchCriteria(
            String keyword,
            List<String> activityTypes,
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
        return new ActivitySearchCriteria(
                keyword,
                activityTypes,
                city,
                parseInstant(startAtFrom),
                parseInstant(startAtTo),
                parseMoney(minFee),
                parseMoney(maxFee),
                latitude,
                longitude,
                distanceMeters,
                page,
                pageSize);
    }

    /**
     * 解析时间参数。
     *
     * <p>前置条件：value 可为空或 ISO-8601 时间字符串。
     *
     * <p>后置条件：空白或格式非法的值返回 null，合法值返回 Instant。
     *
     * <p>不变量：搜索接口没有声明非法查询参数错误分支，非法时间不扩大筛选范围以外的业务状态。
     */
    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * 解析费用参数。
     *
     * <p>前置条件：value 可为空。
     *
     * <p>后置条件：空值返回 null，非空值转为 BigDecimal。
     *
     * <p>不变量：仅用于筛选，不触发支付逻辑。
     */
    private BigDecimal parseMoney(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}

package io.github.layjason.mayoistar.api.activities;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.common.SecurityUtils;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.service.ActivityRegistrationService;
import io.github.layjason.mayoistar.service.ActivityRegistrationStateService;
import io.github.layjason.mayoistar.service.ActivitySearchService;
import io.github.layjason.mayoistar.service.CheckInService;
import io.github.layjason.mayoistar.service.MediaFileUploadService;
import io.github.layjason.mayoistar.service.activities.ActivityDraftService;
import io.github.layjason.mayoistar.service.activities.ActivityQueryService;
import io.github.layjason.mayoistar.service.activities.ActivitySummaryReviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final ActivitySearchService activitySearchService;
    private final SecurityUtils securityUtils;
    private final MediaFileUploadService mediaFileUploadService;

    private final ActivityDraftService activityDraftService;
    private final ActivityQueryService activityQueryService;
    private final ActivitySummaryReviewService activitySummaryReviewService;

    private final ActivityRegistrationService activityRegistrationService;
    private final ActivityRegistrationStateService activityRegistrationStateService;
    private final CheckInService checkInService;

    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> saveDraft(
            @Valid @RequestBody ActivityDtos.ActivityDraftUpsertRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activityDraftService.saveDraft(userId, request)));
    }

    @GetMapping("/drafts")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivityDraftSummary>>> listDrafts(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activityDraftService.listDrafts(userId, page, pageSize)));
    }

    @GetMapping("/drafts/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> getDraft(@PathVariable String activityId) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activityDraftService.getDraft(userId, activityId)));
    }

    @PatchMapping("/drafts/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> updateDraft(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivityDraftUpsertRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activityDraftService.updateDraft(userId, activityId, request)));
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
        return ResponseEntity.ok(ApiResponse.success(activitySearchService.mapPoints(toSearchCriteria(
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
                pageSize))));
    }

    @PostMapping(value = "/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CommonDtos.MediaFile>> uploadActivityImage(
            @RequestPart(value = "file") MultipartFile file) {
        String userId = securityUtils.getCurrentUserId();
        var result = mediaFileUploadService.upload(userId, file, MediaUsage.activityImage);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/media/review-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CommonDtos.MediaFile>> uploadActivityReviewImage(
            @RequestPart(value = "file") MultipartFile file) {
        String userId = securityUtils.getCurrentUserId();
        var result = mediaFileUploadService.upload(userId, file, MediaUsage.activityReviewImage);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummary>>> listMyActivities(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(activityQueryService.listMyActivities(userId, status, page, pageSize)));
    }

    @GetMapping("/registrations/mine")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.RegisteredActivitySummary>>> listMyRegistrations(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activityQueryService.listMyRegistrations(userId, page, pageSize)));
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
        return ResponseEntity.ok(ApiResponse.success(activitySearchService.search(toSearchCriteria(
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
                pageSize))));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivityTemplate>>> listTemplates(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return ResponseEntity.ok(ApiResponse.success(activityDraftService.listTemplates(page, pageSize)));
    }

    @PostMapping("/templates/{templateId}/drafts")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> createDraftFromTemplate(
            @PathVariable String templateId) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activityDraftService.createDraftFromTemplate(userId, templateId)));
    }

    @GetMapping("/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> getActivity(@PathVariable String activityId) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(activityQueryService.getActivity(Optional.of(userId), activityId)));
    }

    @PostMapping("/{activityId}/check-in-qrcode")
    public ResponseEntity<ApiResponse<ActivityDtos.CheckInQrCode>> createCheckInQrCode(
            @PathVariable String activityId) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(checkInService.generateCheckInQrCode(userId, activityId)));
    }

    @PostMapping("/{activityId}/check-ins")
    public ResponseEntity<ApiResponse<ActivityDtos.CheckInRecord>> checkIn(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.CheckInRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(checkInService.checkIn(userId, activityId, request.getQrCodeToken(), request)));
    }

    @GetMapping("/{activityId}/check-ins")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.CheckInRecord>>> listCheckIns(
            @PathVariable String activityId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(checkInService.listCheckIns(userId, activityId, page, pageSize)));
    }

    @GetMapping(value = "/{activityId}/check-ins/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportCheckIns(@PathVariable String activityId) {
        String userId = securityUtils.getCurrentUserId();
        byte[] csvData = checkInService.exportCheckIns(userId, activityId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header("Content-Disposition", "attachment; filename=check-ins-" + activityId + ".csv")
                .body(csvData);
    }

    @PostMapping("/{activityId}/clone")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> cloneActivity(
            @PathVariable String activityId) {
        return responseFactory.activityDraftDetail();
    }

    @GetMapping("/{activityId}/participants")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivityParticipant>>> listParticipants(
            @PathVariable String activityId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        boolean admin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_admin".equals(authority.getAuthority()));
        return ResponseEntity.ok(
                ApiResponse.success(activityQueryService.listParticipants(userId, admin, activityId, page, pageSize)));
    }

    @GetMapping("/{activityId}/participation-state")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityParticipationState>> getParticipationState(
            @PathVariable String activityId) {
        return ResponseEntity.ok(ApiResponse.success(
                activityRegistrationStateService.getParticipationState(activityId, securityUtils.getCurrentUserId())));
    }

    @PostMapping("/{activityId}/registrations")
    public ResponseEntity<ApiResponse<ActivityDtos.RegistrationResult>> registerActivity(
            @PathVariable String activityId, @RequestBody ActivityDtos.RegisterActivityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                activityRegistrationService.registerActivity(activityId, securityUtils.getCurrentUserId(), request)));
    }

    @PostMapping("/{activityId}/registrations/cancel")
    public ResponseEntity<ApiResponse<ActivityDtos.RegistrationResult>> cancelRegistration(
            @PathVariable String activityId) {
        return ResponseEntity.ok(ApiResponse.success(
                activityRegistrationService.cancelRegistration(activityId, securityUtils.getCurrentUserId())));
    }

    @PostMapping("/{activityId}/reviews")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityReview>> reviewActivity(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivityReviewRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(activitySummaryReviewService.createReview(userId, activityId, request)));
    }

    @GetMapping("/{activityId}/reviews")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivityReviewListItem>>> listReviews(
            @PathVariable String activityId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ResponseEntity.ok(
                ApiResponse.success(activitySummaryReviewService.listReviews(activityId, page, pageSize)));
    }

    @GetMapping("/{activityId}/reviews/mine")
    public ResponseEntity<ApiResponse<ActivityDtos.MyActivityReviewResult>> getMyReview(
            @PathVariable String activityId) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activitySummaryReviewService.getMyReview(userId, activityId)));
    }

    @PostMapping("/{activityId}/submit")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDetail>> submitActivity(@PathVariable String activityId) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activityDraftService.submitActivity(userId, activityId)));
    }

    @PostMapping("/{activityId}/summaries")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivitySummaryPost>> createSummary(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivitySummaryPostRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(activitySummaryReviewService.createSummary(userId, activityId, request)));
    }

    @GetMapping("/{activityId}/summaries")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivitySummaryPost>>> listSummaries(
            @PathVariable String activityId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ResponseEntity.ok(
                ApiResponse.success(activitySummaryReviewService.listSummaries(activityId, page, pageSize)));
    }

    @GetMapping("/{activityId}/summaries/mine")
    public ResponseEntity<ApiResponse<ActivityDtos.MyActivitySummaryResult>> getMySummary(
            @PathVariable String activityId) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(activitySummaryReviewService.getMySummary(userId, activityId)));
    }

    @PostMapping("/{activityId}/waiting-confirmations")
    public ResponseEntity<ApiResponse<ActivityDtos.RegistrationResult>> confirmWaitingSeat(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.WaitingConfirmationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                activityRegistrationService.confirmWaitingSeat(activityId, securityUtils.getCurrentUserId(), request)));
    }

    private ActivitySearchService.SearchCriteria toSearchCriteria(
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
        return new ActivitySearchService.SearchCriteria(
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
    }
}

package io.github.layjason.mayoistar.api.activities;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.service.ActivitySearchService;
import jakarta.validation.Valid;
import java.util.List;
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
    private final ActivitySearchService activitySearchService;

    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> saveDraft(
            @Valid @RequestBody ActivityDtos.ActivityDraftUpsertRequest request) {
        return responseFactory.activityDraftDetail();
    }

    @GetMapping("/drafts")
    public ResponseEntity<ApiResponse<PageResult<ActivityDtos.ActivityDraftSummary>>> listDrafts(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.emptyPage();
    }

    @GetMapping("/drafts/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> getDraft(@PathVariable String activityId) {
        return responseFactory.activityDraftDetail();
    }

    @PatchMapping("/drafts/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDtos.ActivityDraftDetail>> updateDraft(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivityDraftUpsertRequest request) {
        return responseFactory.activityDraftDetail();
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
        return responseFactory.emptyPage();
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
        return responseFactory.activityDetail();
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
        return responseFactory.activityDetail();
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

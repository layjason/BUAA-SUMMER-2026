package io.github.layjason.mayoistar.api.activities;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/activities")
public class ActivityController {

    private final DefaultApiResponseFactory responseFactory;

    public ActivityController(DefaultApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<Object>> saveDraft(
            @Valid @RequestBody ActivityDtos.ActivityDraftUpsertRequest request) {
        return responseFactory.success("POST", "/activities/drafts");
    }

    @GetMapping("/drafts")
    public ResponseEntity<ApiResponse<Object>> listDrafts(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/activities/drafts");
    }

    @GetMapping("/drafts/{activityId}")
    public ResponseEntity<ApiResponse<Object>> getDraft(@PathVariable String activityId) {
        return responseFactory.success("GET", "/activities/drafts/" + activityId);
    }

    @PatchMapping("/drafts/{activityId}")
    public ResponseEntity<ApiResponse<Object>> updateDraft(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivityDraftUpsertRequest request) {
        return responseFactory.success("PATCH", "/activities/drafts/" + activityId);
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<Object>> getFeed(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/activities/feed");
    }

    @GetMapping("/map")
    public ResponseEntity<ApiResponse<Object>> getMapPoints(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) List<String> tags) {
        return responseFactory.success("GET", "/activities/map");
    }

    @PostMapping(value = "/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> uploadActivityImage(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return responseFactory.success("POST", "/activities/media/images");
    }

    @PostMapping(value = "/media/review-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> uploadActivityReviewImage(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return responseFactory.success("POST", "/activities/media/review-images");
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<Object>> listMyActivities(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/activities/mine");
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Object>> searchActivities(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/activities/search");
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<Object>> listTemplates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/activities/templates");
    }

    @PostMapping("/templates/{templateId}/drafts")
    public ResponseEntity<ApiResponse<Object>> createDraftFromTemplate(@PathVariable String templateId) {
        return responseFactory.success("POST", "/activities/templates/" + templateId + "/drafts");
    }

    @GetMapping("/{activityId}")
    public ResponseEntity<ApiResponse<Object>> getActivity(@PathVariable String activityId) {
        return responseFactory.success("GET", "/activities/" + activityId);
    }

    @PostMapping("/{activityId}/check-in-qrcode")
    public ResponseEntity<ApiResponse<Object>> createCheckInQrCode(@PathVariable String activityId) {
        return responseFactory.success("POST", "/activities/" + activityId + "/check-in-qrcode");
    }

    @PostMapping("/{activityId}/check-ins")
    public ResponseEntity<ApiResponse<Object>> checkIn(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.CheckInRequest request) {
        return responseFactory.success("POST", "/activities/" + activityId + "/check-ins");
    }

    @GetMapping("/{activityId}/check-ins")
    public ResponseEntity<ApiResponse<Object>> listCheckIns(
            @PathVariable String activityId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/activities/" + activityId + "/check-ins");
    }

    @GetMapping(value = "/{activityId}/check-ins/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> exportCheckIns(@PathVariable String activityId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new byte[] {0});
    }

    @PostMapping("/{activityId}/clone")
    public ResponseEntity<ApiResponse<Object>> cloneActivity(@PathVariable String activityId) {
        return responseFactory.success("POST", "/activities/" + activityId + "/clone");
    }

    @GetMapping("/{activityId}/participants")
    public ResponseEntity<ApiResponse<Object>> listParticipants(
            @PathVariable String activityId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/activities/" + activityId + "/participants");
    }

    @GetMapping("/{activityId}/participation-state")
    public ResponseEntity<ApiResponse<Object>> getParticipationState(@PathVariable String activityId) {
        return responseFactory.success("GET", "/activities/" + activityId + "/participation-state");
    }

    @PostMapping("/{activityId}/registrations")
    public ResponseEntity<ApiResponse<Object>> registerActivity(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.RegisterActivityRequest request) {
        return responseFactory.success("POST", "/activities/" + activityId + "/registrations");
    }

    @PostMapping("/{activityId}/registrations/cancel")
    public ResponseEntity<ApiResponse<Object>> cancelRegistration(@PathVariable String activityId) {
        return responseFactory.success("POST", "/activities/" + activityId + "/registrations/cancel");
    }

    @PostMapping("/{activityId}/reviews")
    public ResponseEntity<ApiResponse<Object>> reviewActivity(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivityReviewRequest request) {
        return responseFactory.success("POST", "/activities/" + activityId + "/reviews");
    }

    @PostMapping("/{activityId}/submit")
    public ResponseEntity<ApiResponse<Object>> submitActivity(@PathVariable String activityId) {
        return responseFactory.success("POST", "/activities/" + activityId + "/submit");
    }

    @PostMapping("/{activityId}/summaries")
    public ResponseEntity<ApiResponse<Object>> createSummary(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.ActivitySummaryPostRequest request) {
        return responseFactory.success("POST", "/activities/" + activityId + "/summaries");
    }

    @PostMapping("/{activityId}/waiting-confirmations")
    public ResponseEntity<ApiResponse<Object>> confirmWaitingSeat(
            @PathVariable String activityId, @Valid @RequestBody ActivityDtos.WaitingConfirmationRequest request) {
        return responseFactory.success("POST", "/activities/" + activityId + "/waiting-confirmations");
    }
}

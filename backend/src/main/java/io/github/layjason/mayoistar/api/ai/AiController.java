package io.github.layjason.mayoistar.api.ai;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.common.SecurityUtils;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.service.ai.ActivityPlanningService;
import io.github.layjason.mayoistar.service.ai.AiRateLimiter;
import io.github.layjason.mayoistar.service.ai.ImageClassificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class AiController {

    private static final String OPERATION_ACTIVITY_PLANNING = "activity-planning";
    private static final String OPERATION_IMAGE_CLASSIFICATION = "image-classification";

    private final ActivityPlanningService activityPlanningService;
    private final ObjectProvider<ImageClassificationService> imageClassificationServiceProvider;
    private final AiRateLimiter aiRateLimiter;
    private final SecurityUtils securityUtils;

    @PostMapping("/activity-plans")
    public ResponseEntity<ApiResponse<AiDtos.ActivityPlanningResult>> generateActivityPlan(
            @Valid @RequestBody AiDtos.ActivityPlanningRequest request) {
        checkRateLimit(OPERATION_ACTIVITY_PLANNING);
        AiDtos.ActivityPlanningResult result = activityPlanningService.generateActivityPlan(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 提交图片分类任务（异步）。
     *
     * <p>前置条件：用户已认证，mediaIds 对应的媒体文件已上传。
     *
     * <p>后置条件：返回 taskId 和初始状态，客户端通过 WebSocket 订阅结果或轮询 GET 接口获取最终结果。
     */
    @PostMapping("/image-classifications")
    public ResponseEntity<ApiResponse<AiDtos.ClassifyTaskSubmitResponse>> submitClassifyTask(
            @Valid @RequestBody AiDtos.ImageClassificationRequest request) {
        checkRateLimit(OPERATION_IMAGE_CLASSIFICATION);
        String userId = securityUtils.getCurrentUserId();
        AiDtos.ClassifyTaskSubmitResponse result =
                getImageClassificationService().submitClassifyTask(request.getMediaIds(), userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 查询分类任务结果。
     *
     * <p>前置条件：taskId 由 POST /image-classifications 返回。
     *
     * <p>后置条件：返回任务状态和分类结果。未分类或不存在时返回相应错误码。
     */
    @GetMapping("/image-classifications/{taskId}")
    public ResponseEntity<ApiResponse<AiDtos.ClassifyTaskQueryResponse>> getClassifyTaskResult(
            @PathVariable UUID taskId) {
        AiDtos.ClassifyTaskQueryResponse result =
                getImageClassificationService().getClassifyTaskResult(taskId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 按 mediaId 查询单张图片的分类缓存。
     *
     * <p>前置条件：mediaId 对应已上传的媒体文件。
     *
     * <p>后置条件：命中缓存时返回分类结果（含 classifiedAt）；
     * 未命中时返回 30004 AI_TASK_NOT_FOUND。
     */
    @GetMapping("/image-classifications/media/{mediaId}")
    public ResponseEntity<ApiResponse<AiDtos.MediaClassificationResponse>> getClassificationByMediaId(
            @PathVariable UUID mediaId) {
        AiDtos.MediaClassificationResponse result =
                getImageClassificationService().getClassificationByMediaId(mediaId);
        if (result == null) {
            throw new BusinessException(ErrorCodes.AI_TASK_NOT_FOUND, "No classification cached for this media");
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private ImageClassificationService getImageClassificationService() {
        ImageClassificationService service = imageClassificationServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(
                    ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI image classification service is unavailable");
        }
        return service;
    }

    private void checkRateLimit(String operation) {
        String userId = securityUtils.getCurrentUserId();
        if (!aiRateLimiter.tryAcquire(userId, operation)) {
            throw new BusinessException(ErrorCodes.AI_RATE_LIMITED, "AI rate limit has been exceeded");
        }
    }
}

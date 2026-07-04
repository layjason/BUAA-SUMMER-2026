package io.github.layjason.mayoistar.api.ai;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.service.ai.ActivityPlanningService;
import io.github.layjason.mayoistar.service.ai.ImageClassificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class AiController {

    private final ActivityPlanningService activityPlanningService;
    private final ImageClassificationService imageClassificationService;

    @PostMapping("/activity-plans")
    public ResponseEntity<ApiResponse<AiDtos.ActivityPlanningResult>> generateActivityPlan(
            @Valid @RequestBody AiDtos.ActivityPlanningRequest request) {
        AiDtos.ActivityPlanningResult result = activityPlanningService.generateActivityPlan(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/image-classifications")
    public ResponseEntity<ApiResponse<AiDtos.ImageClassificationResult>> classifyImages(
            @Valid @RequestBody AiDtos.ImageClassificationRequest request) {
        AiDtos.ImageClassificationResult result = imageClassificationService.classifyImages(request.getMediaIds());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}

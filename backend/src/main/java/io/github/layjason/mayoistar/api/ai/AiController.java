package io.github.layjason.mayoistar.api.ai;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
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

    private final DefaultApiResponseFactory responseFactory;

    @PostMapping("/activity-plans")
    public ResponseEntity<ApiResponse<AiDtos.ActivityPlanningResult>> generateActivityPlan(
            @Valid @RequestBody AiDtos.ActivityPlanningRequest request) {
        return responseFactory.activityPlanningResult();
    }

    @PostMapping("/image-classifications")
    public ResponseEntity<ApiResponse<AiDtos.ImageClassificationResult>> classifyImages(
            @Valid @RequestBody AiDtos.ImageClassificationRequest request) {
        return responseFactory.imageClassificationResult();
    }
}

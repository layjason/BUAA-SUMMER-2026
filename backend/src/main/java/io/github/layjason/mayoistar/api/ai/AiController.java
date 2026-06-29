package io.github.layjason.mayoistar.api.ai;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final DefaultApiResponseFactory responseFactory;

    public AiController(DefaultApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @PostMapping("/activity-plans")
    public ResponseEntity<ApiResponse<Object>> generateActivityPlan(
            @Valid @RequestBody AiDtos.ActivityPlanningRequest request) {
        return responseFactory.success("POST", "/ai/activity-plans");
    }

    @PostMapping("/image-classifications")
    public ResponseEntity<ApiResponse<Object>> classifyImages(
            @Valid @RequestBody AiDtos.ImageClassificationRequest request) {
        return responseFactory.success("POST", "/ai/image-classifications");
    }
}

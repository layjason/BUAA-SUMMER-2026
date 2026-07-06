package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.ai.AiController;
import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.common.SecurityUtils;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.service.ai.ActivityPlanningService;
import io.github.layjason.mayoistar.service.ai.AiRateLimiter;
import io.github.layjason.mayoistar.service.ai.ImageClassificationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;

/**
 * AiController 单元测试。
 *
 * <p>类职责：验证 AI 控制器会执行频率限制，并将活动策划请求委托给真实服务层。
 */
class AiControllerTests {

    private ActivityPlanningService activityPlanningService;
    private ImageClassificationService imageClassificationService;
    private AiRateLimiter aiRateLimiter;
    private SecurityUtils securityUtils;
    private AiController controller;

    @BeforeEach
    void setUp() {
        activityPlanningService = mock(ActivityPlanningService.class);
        imageClassificationService = mock(ImageClassificationService.class);
        aiRateLimiter = mock(AiRateLimiter.class);
        securityUtils = mock(SecurityUtils.class);
        controller = new AiController(
                activityPlanningService, imageClassificationServiceProvider(), aiRateLimiter, securityUtils);
    }

    @Test
    @DisplayName("活动策划接口应通过限流后委托服务层生成结果")
    void generateActivityPlanShouldDelegateToServiceAfterRateLimit() {
        AiDtos.ActivityPlanningRequest request = request();
        AiDtos.ActivityPlanningResult result = result();
        when(securityUtils.getCurrentUserId()).thenReturn("user-a");
        when(aiRateLimiter.tryAcquire("user-a", "activity-planning")).thenReturn(true);
        when(activityPlanningService.generateActivityPlan(request)).thenReturn(result);

        ResponseEntity<ApiResponse<AiDtos.ActivityPlanningResult>> response = controller.generateActivityPlan(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(result);
        assertThat(response.getBody().getData().getTitle()).isEqualTo("模型生成活动");
        verify(activityPlanningService).generateActivityPlan(request);
    }

    @Test
    @DisplayName("活动策划超出频率限制时应抛出 AI 限流业务异常")
    void generateActivityPlanShouldThrowRateLimitedWhenQuotaExceeded() {
        AiDtos.ActivityPlanningRequest request = request();
        when(securityUtils.getCurrentUserId()).thenReturn("user-a");
        when(aiRateLimiter.tryAcquire("user-a", "activity-planning")).thenReturn(false);

        assertThatThrownBy(() -> controller.generateActivityPlan(request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCodes.AI_RATE_LIMITED);
                    assertThat(exception.getBusinessMessage()).isEqualTo("AI rate limit has been exceeded");
                });
        verify(activityPlanningService, never()).generateActivityPlan(request);
    }

    @Test
    @DisplayName("图片分类接口也应消费 AI 限流配置")
    void classifyImagesShouldCheckAiRateLimit() {
        AiDtos.ImageClassificationRequest request = new AiDtos.ImageClassificationRequest();
        request.setMediaIds(List.of());
        when(securityUtils.getCurrentUserId()).thenReturn("user-a");
        when(aiRateLimiter.tryAcquire("user-a", "image-classification")).thenReturn(false);

        assertThatThrownBy(() -> controller.submitClassifyTask(request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCodes.AI_RATE_LIMITED);
                    assertThat(exception.getBusinessMessage()).isEqualTo("AI rate limit has been exceeded");
                });
        verify(imageClassificationService, never()).submitClassifyTask(request.getMediaIds(), "user-a");
    }

    private ObjectProvider<ImageClassificationService> imageClassificationServiceProvider() {
        return new ObjectProvider<>() {
            @Override
            public ImageClassificationService getObject() {
                return imageClassificationService;
            }

            @Override
            public ImageClassificationService getIfAvailable() {
                return imageClassificationService;
            }
        };
    }

    private AiDtos.ActivityPlanningRequest request() {
        AiDtos.ActivityPlanningRequest request = new AiDtos.ActivityPlanningRequest();
        request.setTopic("羽毛球活动");
        return request;
    }

    private AiDtos.ActivityPlanningResult result() {
        AiDtos.ActivityPlanningResult result = new AiDtos.ActivityPlanningResult();
        result.setStatus("succeeded");
        result.setTitle("模型生成活动");
        result.setTags(List.of("运动"));
        return result;
    }
}

package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ActivityPlanningContentSafetyGuard 单元测试。
 *
 * <p>类职责：验证 AI 活动策划输出会经过二次文本内容安全审核。
 */
class ActivityPlanningContentSafetyGuardTest {

    @Test
    @DisplayName("低风险输出应通过二次内容安全审核")
    void shouldAllowLowRiskOutput() {
        FakeContentReviewClient client = new FakeContentReviewClient(ContentReviewScanResult.low());
        ActivityPlanningContentSafetyGuard guard = new ActivityPlanningContentSafetyGuard(client);

        guard.review(result());

        assertThat(client.reviewText).contains("活动名称：周末桌游破冰夜");
        assertThat(client.reviewText).contains("活动简介：面向新朋友的轻松桌游活动");
        assertThat(client.reviewText).contains("安全须知：请遵守场地秩序");
    }

    @Test
    @DisplayName("需复核输出应被拦截")
    void shouldRejectReviewRiskOutput() {
        FakeContentReviewClient client = new FakeContentReviewClient(
                new ContentReviewScanResult(ContentReviewRisk.review, List.of("需复核"), null));
        ActivityPlanningContentSafetyGuard guard = new ActivityPlanningContentSafetyGuard(client);

        assertThatThrownBy(() -> guard.review(result()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI activity planning output failed content safety review");
    }

    @Test
    @DisplayName("内容审核失败时应被拦截")
    void shouldRejectFailedReviewOutput() {
        FakeContentReviewClient client = new FakeContentReviewClient(ContentReviewScanResult.failed("审核服务不可用"));
        ActivityPlanningContentSafetyGuard guard = new ActivityPlanningContentSafetyGuard(client);

        assertThatThrownBy(() -> guard.review(result()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI activity planning output failed content safety review");
    }

    private AiDtos.ActivityPlanningResult result() {
        AiDtos.ActivityPlanningResult result = new AiDtos.ActivityPlanningResult();
        result.setTitle("周末桌游破冰夜");
        result.setTags(List.of("桌游", "社交", "新手友好"));
        result.setIntroduction("面向新朋友的轻松桌游活动");
        result.setSafetyNotice("请遵守场地秩序");
        return result;
    }

    private static class FakeContentReviewClient implements ContentReviewClient {

        private final ContentReviewScanResult textResult;
        private String reviewText;

        private FakeContentReviewClient(ContentReviewScanResult textResult) {
            this.textResult = textResult;
        }

        @Override
        public ContentReviewScanResult scanText(String content) {
            reviewText = content;
            return textResult;
        }

        @Override
        public ContentReviewScanResult scanImages(List<MediaFile> images) {
            return ContentReviewScanResult.low();
        }
    }
}

package io.github.layjason.mayoistar.service.activities;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.service.ai.ContentReviewClient;
import io.github.layjason.mayoistar.service.ai.ContentReviewRisk;
import io.github.layjason.mayoistar.service.ai.ContentReviewScanResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ActivityContentReviewService 单元测试。
 *
 * <p>类职责：验证活动内容审核聚合文本与图片风险时的状态映射和兜底行为。
 *
 * <p>类不变量：测试使用内存 Fake 客户端，不调用真实内容审核 API。
 */
class ActivityContentReviewServiceTest {

    private FakeContentReviewClient client;
    private ActivityContentReviewService service;

    @BeforeEach
    void setUp() {
        client = new FakeContentReviewClient();
        service = new ActivityContentReviewService(client);
    }

    @Test
    @DisplayName("文本低风险且无图片时应建议审核通过")
    void shouldApproveWhenTextLowRiskAndNoImages() {
        AiDtos.AiContentReviewResult result = service.reviewActivity(activity(), List.of());

        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getRiskLevel()).isEqualTo("low");
        assertThat(result.getSuggestedReviewStatus()).isEqualTo(ReviewStatus.approved);
        assertThat(result.getReasons()).containsExactly("文本审核：未发现内容安全风险");
        assertThat(client.lastText).contains("活动名称：周末羽毛球");
        assertThat(client.imageCallCount).isZero();
    }

    @Test
    @DisplayName("图片阻断风险应覆盖文本低风险并建议驳回")
    void shouldRejectWhenImageBlocked() {
        client.imageResult = new ContentReviewScanResult(ContentReviewRisk.block, List.of("图片命中违规标识"), null);

        AiDtos.AiContentReviewResult result = service.reviewActivity(activity(), List.of(image()));

        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getRiskLevel()).isEqualTo("high");
        assertThat(result.getSuggestedReviewStatus()).isEqualTo(ReviewStatus.rejected);
        assertThat(result.getReasons()).contains("图片审核：图片命中违规标识");
        assertThat(client.imageCallCount).isEqualTo(1);
    }

    @Test
    @DisplayName("文本审核调用失败时应返回 failed 并建议转人工")
    void shouldReturnFailedWhenTextReviewUnavailable() {
        client.textResult = ContentReviewScanResult.failed("阿里云文本内容审核失败：InvalidAccessKeyId");

        AiDtos.AiContentReviewResult result = service.reviewActivity(activity(), List.of(image()));

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getRiskLevel()).isEqualTo("uncertain");
        assertThat(result.getSuggestedReviewStatus()).isEqualTo(ReviewStatus.pending);
        assertThat(result.getFriendlyErrorMessage()).isEqualTo("阿里云文本内容审核失败：InvalidAccessKeyId");
    }

    private Activity activity() {
        return Activity.builder()
                .activityId("activity-review-sample")
                .organizerId("user-review-sample")
                .title("周末羽毛球")
                .tags(List.of("运动", "羽毛球"))
                .introduction("适合新手的轻松羽毛球活动")
                .city("北京")
                .placeName("五道口体育馆")
                .address("学院路 1 号")
                .startAt(Instant.parse("2026-07-10T10:00:00Z"))
                .endAt(Instant.parse("2026-07-10T12:00:00Z"))
                .registrationDeadline(Instant.parse("2026-07-09T12:00:00Z"))
                .capacity(16)
                .minAge(18)
                .feeDescription("AA 场地费")
                .safetyNotice("请提前热身")
                .build();
    }

    private MediaFile image() {
        return MediaFile.builder()
                .mediaId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .fileName("activity.png")
                .contentType("image/png")
                .sizeBytes(1024L)
                .storagePath("activity/review/activity.png")
                .uploadedBy("user-review-sample")
                .build();
    }

    private static class FakeContentReviewClient implements ContentReviewClient {

        private ContentReviewScanResult textResult = ContentReviewScanResult.low();
        private ContentReviewScanResult imageResult = ContentReviewScanResult.low();
        private String lastText;
        private int imageCallCount;

        @Override
        public ContentReviewScanResult scanText(String content) {
            lastText = content;
            return textResult;
        }

        @Override
        public ContentReviewScanResult scanImages(List<MediaFile> images) {
            imageCallCount++;
            return imageResult;
        }
    }
}

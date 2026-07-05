package io.github.layjason.mayoistar.config;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.service.ai.ContentReviewClient;
import io.github.layjason.mayoistar.service.ai.ContentReviewScanResult;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * 测试内容审核配置。
 *
 * <p>类职责：在 test profile 下替换真实阿里云审核客户端，避免单元测试调用外部 AI API。
 *
 * <p>类不变量：默认文本与图片审核均返回低风险，可由测试用例显式设置下一次结果。
 */
@Configuration
@Profile("test")
public class TestContentReviewConfiguration {

    @Bean
    @Primary
    public FakeContentReviewClient fakeContentReviewClient() {
        return new FakeContentReviewClient();
    }

    public static class FakeContentReviewClient implements ContentReviewClient {

        private ContentReviewScanResult textResult = ContentReviewScanResult.low();
        private ContentReviewScanResult imageResult = ContentReviewScanResult.low();

        @Override
        public ContentReviewScanResult scanText(String content) {
            return textResult;
        }

        @Override
        public ContentReviewScanResult scanImages(List<MediaFile> images) {
            return imageResult;
        }

        public void setTextResult(ContentReviewScanResult textResult) {
            this.textResult = textResult;
        }

        public void setImageResult(ContentReviewScanResult imageResult) {
            this.imageResult = imageResult;
        }

        public void reset() {
            textResult = ContentReviewScanResult.low();
            imageResult = ContentReviewScanResult.low();
        }
    }
}

package io.github.layjason.mayoistar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI 服务配置属性。
 *
 * <p>类职责：绑定 application.yaml 中 mayoistar.ai 前缀的配置项。
 *
 * <p>类不变量：所有字段均有默认值，确保本地开发环境可直接启动。
 */
@Data
@Validated
@ConfigurationProperties("mayoistar.ai")
public class AiProperties {

    private Clip clip = new Clip();

    private ContentReview contentReview = new ContentReview();

    private RateLimit rateLimit = new RateLimit();

    /**
     * CLIP 图片分类边车服务配置。
     */
    @Data
    public static class Clip {

        /** CLIP 服务端点地址（废弃：Kafka 替代） */
        private String endpoint = "http://localhost:8000";

        /** HTTP 请求超时时间（秒） */
        private int timeoutSeconds = 60;

        /** Kafka 请求 Topic 名称 */
        private String requestTopic = "clip-classify-request";

        /** Kafka 响应 Topic 名称 */
        private String responseTopic = "clip-classify-response";

        /** Kafka 死信 Topic 名称 */
        private String requestDlqTopic = "clip-classify-request-dlq";

        /** 任务超时时间（秒） */
        private int requestTimeoutSeconds = 30;

        /** Kafka consumer 最大重试次数 */
        private int requestRetryMax = 3;
    }

    /**
     * AI 内容安全审核配置。
     */
    @Data
    public static class ContentReview {

        /** 内容审核服务端点 */
        private String endpoint = "";

        /** 内容审核 AccessKey ID，默认由 ALIBABA_CLOUD_ACCESS_KEY_ID 注入 */
        private String accessKeyId = "";

        /** 内容审核 AccessKey Secret，默认由 ALIBABA_CLOUD_ACCESS_KEY_SECRET 注入 */
        private String accessKeySecret = "";

        /** 文本审核标签 */
        private java.util.List<String> textLabels = java.util.List.of("ad");

        /** 图片审核场景 */
        private java.util.List<String> imageScenes = java.util.List.of("porn", "logo");
    }

    /**
     * AI 调用频率限制配置。
     */
    @Data
    public static class RateLimit {

        /** 每分钟最大调用次数 */
        private int maxRequestsPerMinute = 20;
    }
}

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

    private RateLimit rateLimit = new RateLimit();

    /**
     * CLIP 图片分类边车服务配置。
     */
    @Data
    public static class Clip {

        /** CLIP 服务端点地址 */
        private String endpoint = "http://localhost:8000";

        /** HTTP 请求超时时间（秒） */
        private int timeoutSeconds = 60;
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

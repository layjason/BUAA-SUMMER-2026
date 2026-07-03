package io.github.layjason.mayoistar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 媒体签名访问配置。
 *
 * <p>类职责：绑定媒体签名 URL 的密钥。
 *
 * <p>不变量：签名密钥由 YAML 配置或环境变量提供，禁止在代码中硬编码默认值。
 */
@Data
@Validated
@ConfigurationProperties("mayoistar.media.access")
public class MediaAccessProperties {

    private String signingSecret;
}

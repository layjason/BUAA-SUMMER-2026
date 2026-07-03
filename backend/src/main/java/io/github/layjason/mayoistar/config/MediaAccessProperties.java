package io.github.layjason.mayoistar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 媒体签名访问配置。
 *
 * <p>类职责：绑定媒体签名 URL 的密钥。
 *
 * <p>不变量：签名密钥必须由部署环境覆盖，默认值仅用于本地开发和测试。
 */
@Data
@Validated
@ConfigurationProperties("mayoistar.media.access")
public class MediaAccessProperties {

    private String signingSecret = "dev-media-signing-secret-change-me";
}

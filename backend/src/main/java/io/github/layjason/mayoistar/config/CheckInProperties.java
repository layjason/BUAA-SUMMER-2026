package io.github.layjason.mayoistar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 签到功能配置属性。
 *
 * <p>类职责：绑定签到 QR 码 token 的签名密钥和过期时间。
 *
 * <p>不变量：签名密钥必须由部署环境覆盖，默认值仅用于本地开发和测试。
 */
@Data
@Validated
@ConfigurationProperties("mayoistar.check-in")
public class CheckInProperties {

    /**
     * 签到 QR 码 token 的 HMAC 签名密钥。
     */
    private String signingSecret = "dev-checkin-signing-secret-change-me";

    /**
     * 签到 QR 码 token 有效期（秒），默认 900 秒（15 分钟）。
     */
    private long tokenExpireSeconds = 900;

    /**
     * 签到时位置校验的允许距离（米），默认 500 米。
     */
    private double locationCheckMeters = 500;
}

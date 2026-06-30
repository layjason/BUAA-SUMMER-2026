package io.github.layjason.mayoistar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性。
 *
 * <p>类职责：绑定 mayoistar.jwt 前缀的配置项。
 *
 * <p>类不变量：字段值来自配置文件或环境变量，不可变。
 *
 * @param secret              HMAC-SHA256 签名密钥，须从环境变量注入
 * @param accessTokenExpire   访问令牌有效期（秒），默认 3600
 * @param refreshTokenExpire  刷新令牌有效期（秒），默认 2592000（30 天）
 */
@ConfigurationProperties(prefix = "mayoistar.jwt")
public record JwtProperties(String secret, long accessTokenExpire, long refreshTokenExpire) {}

package io.github.layjason.mayoistar.service.media;

import java.time.Instant;

/**
 * 已签名媒体访问地址。
 *
 * @param signedUrl 签名 URL
 * @param expiresAt 过期时间
 */
public record SignedMediaAccess(String signedUrl, Instant expiresAt) {}

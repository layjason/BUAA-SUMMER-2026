package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.config.JwtProperties;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * JWT 令牌服务。
 *
 * <p>类职责：生成和校验 JWT 访问令牌与刷新令牌。刷新令牌也以 JWT 形式签发，但过期时间更长。
 *
 * <p>不变量：密钥从配置注入，令牌 payload 中的 userId / userKind 在有效期内的令牌中总是存在。
 */
@Slf4j
@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * @param jwtProperties JWT 配置属性
     */
    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 bytes)");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成访问令牌。
     *
     * <p>前置条件：userId 非空，userKind 非空。
     *
     * <p>后置条件：返回包含 sub=userId、kind=userKind、type=access 且有效期为 accessTokenExpire 秒的 JWT。
     *
     * @param userId   用户标识
     * @param userKind 用户类型
     * @return 访问令牌字符串
     */
    public String generateAccessToken(String userId, UserKind userKind) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("kind", userKind.name())
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProperties.accessTokenExpire())))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 生成刷新令牌。
     *
     * <p>前置条件：userId 非空，userKind 非空。
     *
     * <p>后置条件：返回包含 sub=userId、kind=userKind、type=refresh 且有效期为 refreshTokenExpire 秒的 JWT。
     *
     * @param userId   用户标识
     * @param userKind 用户类型
     * @return 刷新令牌字符串
     */
    public String generateRefreshToken(String userId, UserKind userKind) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("kind", userKind.name())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProperties.refreshTokenExpire())))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 校验并解析 JWT。
     *
     * <p>前置条件：token 非空。
     *
     * <p>后置条件：若 token 有效（签名正确、未过期），返回 Claims；否则返回 null。
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims，无效时返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT 令牌解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从令牌中提取用户 ID。
     *
     * <p>前置条件：token 已通过 {@link #parseToken} 校验。
     *
     * <p>后置条件：返回 subject 声明的值。
     *
     * @param claims 解析后的 Claims
     * @return 用户 ID
     */
    public String getUserId(Claims claims) {
        return claims.getSubject();
    }

    /**
     * 从令牌中提取用户类型。
     *
     * <p>前置条件：token 已通过 {@link #parseToken} 校验。
     *
     * <p>后置条件：返回 kind 声明的值。
     *
     * @param claims 解析后的 Claims
     * @return 用户类型
     */
    public UserKind getUserKind(Claims claims) {
        return UserKind.valueOf(claims.get("kind", String.class));
    }

    /**
     * 生成管理员访问令牌。
     *
     * <p>前置条件：adminId 非空。
     *
     * <p>后置条件：返回包含 sub=adminId、kind=admin、type=access 且有效期为 accessTokenExpire 秒的 JWT。
     *
     * @param adminId 管理员标识
     * @return 访问令牌字符串
     */
    public String generateAdminAccessToken(String adminId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(adminId)
                .claim("kind", "admin")
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProperties.accessTokenExpire())))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 生成管理员刷新令牌。
     *
     * <p>前置条件：adminId 非空。
     *
     * <p>后置条件：返回包含 sub=adminId、kind=admin、type=refresh 且有效期为 refreshTokenExpire 秒的 JWT。
     *
     * @param adminId 管理员标识
     * @return 刷新令牌字符串
     */
    public String generateAdminRefreshToken(String adminId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(adminId)
                .claim("kind", "admin")
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProperties.refreshTokenExpire())))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 检查 Claims 是否为管理员令牌。
     *
     * <p>前置条件：claims 非空。
     *
     * <p>后置条件：若 kind 声称值为 "admin" 则返回 true。
     *
     * @param claims 解析后的 Claims
     * @return 是否为管理员令牌
     */
    public boolean isAdmin(Claims claims) {
        return "admin".equals(claims.get("kind", String.class));
    }

    /**
     * 获取访问令牌的过期时间。
     *
     * @param claims 解析后的 Claims
     * @return 过期时间的 ISO 8601 字符串
     */
    public String getExpiresAt(Claims claims) {
        return claims.getExpiration().toInstant().toString();
    }

    /**
     * 获取刷新令牌的有效期（秒）。
     *
     * @return 刷新令牌有效期
     */
    public long getRefreshTokenExpireSeconds() {
        return jwtProperties.refreshTokenExpire();
    }

    /**
     * 生成安全的随机令牌字符串。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回 32 字节随机数 Base64 URL-safe 编码的字符串。
     *
     * @return 随机令牌
     */
    public static String generateRandomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 对令牌进行哈希，用于安全存储。
     *
     * <p>前置条件：token 非空。
     *
     * <p>后置条件：返回 token 的 SHA-256 哈希的十六进制字符串。
     *
     * @param token 原始令牌
     * @return 哈希值
     */
    public static String hashToken(String token) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }
}

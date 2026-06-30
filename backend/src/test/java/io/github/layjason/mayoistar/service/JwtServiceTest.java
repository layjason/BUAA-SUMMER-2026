package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.config.JwtProperties;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                new JwtProperties("test-secret-key-that-is-at-least-256-bits-long-for-hs256", 3600, 2592000));
    }

    @Test
    @DisplayName("生成并校验访问令牌")
    void shouldGenerateAndValidateAccessToken() {
        String token = jwtService.generateAccessToken("user-123", UserKind.personal);
        Claims claims = jwtService.parseToken(token);

        assertThat(claims).isNotNull();
        assertThat(jwtService.getUserId(claims)).isEqualTo("user-123");
        assertThat(jwtService.getUserKind(claims)).isEqualTo(UserKind.personal);
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(jwtService.getExpiresAt(claims)).isNotBlank();
    }

    @Test
    @DisplayName("生成并校验刷新令牌")
    void shouldGenerateAndValidateRefreshToken() {
        String token = jwtService.generateRefreshToken("user-123", UserKind.merchant);
        Claims claims = jwtService.parseToken(token);

        assertThat(claims).isNotNull();
        assertThat(jwtService.getUserId(claims)).isEqualTo("user-123");
        assertThat(jwtService.getUserKind(claims)).isEqualTo(UserKind.merchant);
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("无效令牌返回 null")
    void shouldReturnNullForInvalidToken() {
        Claims claims = jwtService.parseToken("invalid-token");
        assertThat(claims).isNull();
    }

    @Test
    @DisplayName("生成随机令牌非空且长度合适")
    void shouldGenerateRandomToken() {
        String token = JwtService.generateRandomToken();
        assertThat(token).isNotBlank();
        assertThat(token.length()).isGreaterThan(32);
    }

    @Test
    @DisplayName("哈希令牌产生固定长度输出")
    void shouldHashToken() {
        String hash1 = JwtService.hashToken("token1");
        String hash2 = JwtService.hashToken("token1");
        String hash3 = JwtService.hashToken("token2");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(hash3);
        assertThat(hash1.length()).isEqualTo(64); // SHA-256 输出 64 十六进制字符
    }
}

package io.github.layjason.mayoistar.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 二维码生成与解析服务。
 *
 * <p>类职责：为用户生成包含签名令牌的二维码图片，并解析扫描结果提取用户标识。
 *
 * <p>不变量：二维码令牌有效期固定为 5 分钟，防止重放攻击。
 */
@Slf4j
@Service
public class QrCodeService {

    private static final int QR_CODE_SIZE = 300;
    private static final long TOKEN_VALIDITY_MINUTES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    /**
     * 构造二维码服务，从 JWT 密钥派生签名密钥。
     *
     * @param jwtSecret JWT 配置密钥
     */
    public QrCodeService(@Value("${mayoistar.jwt.secret}") String jwtSecret) {
        this.secretKey = createKey(jwtSecret);
    }

    private static SecretKey createKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("QR token secret must be at least 256 bits (32 bytes)");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 为用户生成二维码图片（PNG）。
     *
     * <p>前置条件：userId 非空且对应有效用户。
     *
     * <p>后置条件：返回包含签名令牌的 PNG 二维码字节数组。
     *
     * @param userId 用户标识
     * @return PNG 二维码图片字节数组
     */
    public byte[] generateQrCode(String userId) {
        String token = createToken(userId);
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(token, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            log.info("二维码已生成: userId={}", userId);
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("二维码生成失败: userId={}", userId, e);
            throw new RuntimeException("QR code generation failed", e);
        }
    }

    /**
     * 解析扫描的二维码令牌，提取用户标识。
     *
     * <p>前置条件：encodedToken 为合法的签名 JWT 格式。
     *
     * <p>后置条件：令牌有效时返回存储的用户标识；令牌过期或签名无效时抛出业务异常。
     *
     * @param encodedToken 从二维码解码出的令牌字符串
     * @return 令牌中存储的用户标识
     */
    public String parseQrCode(String encodedToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(encodedToken)
                    .getPayload();
            String userId = claims.getSubject();
            log.info("二维码令牌解析成功: userId={}", userId);
            return userId;
        } catch (ExpiredJwtException e) {
            log.warn("二维码令牌已过期");
            throw new IllegalArgumentException("QR code token has expired");
        } catch (JwtException e) {
            log.warn("二维码令牌签名校验失败");
            throw new IllegalArgumentException("QR code token is invalid");
        }
    }

    /**
     * 创建包含用户标识的短生命周期 JWT 令牌。
     *
     * <p>前置条件：userId 非空。
     *
     * <p>后置条件：返回 5 分钟有效的签名 JWT。
     *
     * @param userId 用户标识
     * @return 签名后的 JWT 令牌字符串
     */
    private String createToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TOKEN_VALIDITY_MINUTES * 60)))
                .signWith(secretKey)
                .compact();
    }
}

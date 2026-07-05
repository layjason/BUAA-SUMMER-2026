package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.QrCodeTestUtil;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * 二维码服务单元测试。
 *
 * <p>类职责：验证 QrCodeService 的生成和解析功能。
 *
 * <p>不变量：测试使用 test profile 的 JWT 密钥。
 */
class QrCodeServiceTest extends AbstractIntegrationTest {

    @Autowired
    private QrCodeService qrCodeService;

    @Value("${mayoistar.jwt.secret}")
    private String jwtSecret;

    /**
     * 验证生成的二维码为有效 PNG 图片。
     *
     * <p>前置条件：Spring 上下文已加载。
     *
     * <p>后置条件：返回的字节数组可被解码为 BufferedImage。
     */
    @Test
    @DisplayName("生成二维码 - 返回有效 PNG 图片")
    void generateQrCode_returnsValidPng() throws Exception {
        byte[] pngBytes = qrCodeService.generateQrCode("test-user-123");

        assertThat(pngBytes).isNotNull().isNotEmpty();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isGreaterThan(0);
        assertThat(image.getHeight()).isGreaterThan(0);
    }

    /**
     * 验证二维码图片中包含可解析的令牌。
     *
     * <p>前置条件：二维码生成成功。
     *
     * <p>后置条件：可通过 ZXing 解码出 JWT 格式令牌字符串。
     */
    @Test
    @DisplayName("生成二维码 - 包含可解码的令牌")
    void generateQrCode_containsDecodableToken() throws Exception {
        byte[] pngBytes = qrCodeService.generateQrCode("test-user-456");

        String token = QrCodeTestUtil.decodeQrCodeFromPng(pngBytes);
        assertThat(token).isNotNull().isNotEmpty().contains(".");
    }

    /**
     * 验证解析合法令牌返回正确的用户标识。
     *
     * <p>前置条件：已生成有效二维码令牌。
     *
     * <p>后置条件：解析结果与原始 userId 一致。
     */
    @Test
    @DisplayName("解析二维码令牌 - 返回正确 userId")
    void parseQrCode_returnsCorrectUserId() throws Exception {
        String token = createQrToken("parse-user-789");

        String userId = qrCodeService.parseQrCode(token);
        assertThat(userId).isEqualTo("parse-user-789");
    }

    /**
     * 验证解析无效令牌抛出异常。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：抛出 BusinessException。
     */
    @Test
    @DisplayName("解析无效令牌 - 抛出异常")
    void parseQrCode_invalidTokenThrows() {
        assertThatThrownBy(() -> qrCodeService.parseQrCode("not-a-valid-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid");
    }

    /**
     * 验证解析空字符串抛出异常。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：抛出 BusinessException。
     */
    @Test
    @DisplayName("解析空令牌 - 抛出异常")
    void parseQrCode_emptyTokenThrows() {
        assertThatThrownBy(() -> qrCodeService.parseQrCode("")).isInstanceOf(BusinessException.class);
    }

    /**
     * 验证每次生成的令牌不同（含唯一 jti 和不同 issuedAt）。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：两次调用生成的令牌字符串不同。
     */
    @Test
    @DisplayName("生成二维码 - 每次令牌不同")
    void generateQrCode_eachCallProducesUniqueToken() throws Exception {
        byte[] png1 = qrCodeService.generateQrCode("same-user");
        byte[] png2 = qrCodeService.generateQrCode("same-user");

        String token1 = QrCodeTestUtil.decodeQrCodeFromPng(png1);
        String token2 = QrCodeTestUtil.decodeQrCodeFromPng(png2);

        assertThat(token1).isNotEqualTo(token2);
    }

    /**
     * 创建与 QrCodeService 签名规则一致的二维码令牌。
     *
     * <p>前置条件：test profile 已提供不少于 256 bit 的 JWT 密钥。
     *
     * <p>后置条件：返回未过期且可被 QrCodeService.parseQrCode 验证的 JWT 字符串。
     */
    private String createQrToken(String userId) {
        SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(secretKey)
                .compact();
    }
}

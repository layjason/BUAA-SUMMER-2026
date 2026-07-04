package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.github.layjason.mayoistar.AbstractIntegrationTest;
import io.github.layjason.mayoistar.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Result result = new MultiFormatReader().decode(bitmap);

        String token = result.getText();
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
        byte[] pngBytes = qrCodeService.generateQrCode("parse-user-789");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Result result = new MultiFormatReader().decode(bitmap);
        String token = result.getText();

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

        BufferedImage img1 = ImageIO.read(new ByteArrayInputStream(png1));
        BufferedImage img2 = ImageIO.read(new ByteArrayInputStream(png2));

        LuminanceSource src1 = new BufferedImageLuminanceSource(img1);
        LuminanceSource src2 = new BufferedImageLuminanceSource(img2);
        BinaryBitmap bmp1 = new BinaryBitmap(new HybridBinarizer(src1));
        BinaryBitmap bmp2 = new BinaryBitmap(new HybridBinarizer(src2));

        String token1 = new MultiFormatReader().decode(bmp1).getText();
        String token2 = new MultiFormatReader().decode(bmp2).getText();

        assertThat(token1).isNotEqualTo(token2);
    }
}

package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ClipServiceClient 单元测试。
 *
 * <p>类职责：验证图片 base64 编码功能的正确性。
 */
class ClipServiceClientTest {

    @Test
    @DisplayName("base64 编码应生成正确的 Data URI 格式")
    void shouldEncodeToValidDataUri() {
        byte[] imageBytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        String result = ClipServiceClient.encodeImage(imageBytes, "image/jpeg");

        assertThat(result).startsWith("data:image/jpeg;base64,");
        assertThat(result).contains("/9j/"); // FF D8 FF 的 base64 编码
    }

    @Test
    @DisplayName("空字节数组编码应��成仅含前缀的 Data URI")
    void shouldEncodeEmptyBytes() {
        String result = ClipServiceClient.encodeImage(new byte[0], "image/png");

        assertThat(result).isEqualTo("data:image/png;base64,");
    }

    @Test
    @DisplayName("PNG 类型编码应包含正确的 MIME 类型")
    void shouldIncludePngContentType() {
        byte[] imageBytes = new byte[] {0x01, 0x02, 0x03};
        String result = ClipServiceClient.encodeImage(imageBytes, "image/png");

        assertThat(result).contains("data:image/png;base64,");
    }
}

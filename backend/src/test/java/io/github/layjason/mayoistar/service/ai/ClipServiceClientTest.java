package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.service.ai.ClipModels.ClipClassifyResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ClipServiceClient 单元测试。
 *
 * <p>类职责：验证图片 base64 编码功能，以及 CLIP HTTP 客户端发送 JSON 请求体的正确性。
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
    @DisplayName("空字节数组编码应生成仅含前缀的 Data URI")
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

    @Test
    @DisplayName("调用分类服务时应发送非空 JSON 请求体")
    void shouldSendJsonBodyWhenClassifyImages() throws IOException {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> requestContentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/classify",
                exchange -> handleClassifyRequest(exchange, requestMethod, requestContentType, requestBody));
        server.start();

        try {
            AiProperties properties = new AiProperties();
            properties
                    .getClip()
                    .setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
            properties.getClip().setTimeoutSeconds(5);
            ClipServiceClient client =
                    new ClipServiceClient(new ClipClientConfig(new ObjectMapper()).clipRestClient(properties));

            ClipClassifyResponse response = client.classify(List.of("data:image/png;base64,AA=="));

            assertThat(response.getItems()).hasSize(1);
            assertThat(requestMethod.get()).isEqualTo("POST");
            assertThat(requestContentType.get()).startsWith("application/json");
            assertThat(requestBody.get()).contains("\"images\"");
            assertThat(requestBody.get()).contains("data:image/png;base64,AA==");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 处理测试中的 CLIP 分类请求。
     *
     * <p>前置条件：exchange 来自 /classify 路径，引用容器均非空。
     *
     * <p>后置条件：记录请求方法、Content-Type 与请求体，并返回合法的分类响应 JSON。
     *
     * <p>不变量：不修改 HTTP server 的路由配置。
     */
    private static void handleClassifyRequest(
            HttpExchange exchange,
            AtomicReference<String> requestMethod,
            AtomicReference<String> requestContentType,
            AtomicReference<String> requestBody)
            throws IOException {
        requestMethod.set(exchange.getRequestMethod());
        requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] responseBody =
                "{\"items\":[{\"category\":\"group_photo\",\"confidence\":0.9}]}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }
}

package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.service.ai.ClipModels.ClipClassifyRequest;
import io.github.layjason.mayoistar.service.ai.ClipModels.ClipClassifyResponse;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * CLIP 图片分类 HTTP 客户端。
 *
 * <p>类职责：封装与 Python CLIP 边车服务的 HTTP 通信，处理图片编码、请求发送、响应解析和错误处理。
 *
 * <p>类不变量：RestClient 在构造时创建并配置超时，请求符合 CLIP 服务的 /classify 端点契约。
 */
@Slf4j
@Component
public class ClipServiceClient {

    private final RestClient restClient;

    /**
     * 构造 CLIP 服务客户端。
     *
     * <p>前置条件：AiProperties 中 clip.endpoint 为有效的 HTTP URL。
     *
     * <p>后置条件：创建已配置超时和 JSON 编解码的 RestClient 实例。
     *
     * @param properties   AI 配置属性
     * @param objectMapper JSON 序列化/反序列化工具
     */
    public ClipServiceClient(AiProperties properties) {
        this.restClient =
                RestClient.builder().baseUrl(properties.getClip().getEndpoint()).build();
    }

    /**
     * 将图片字节数组编码为 base64 Data URI。
     *
     * <p>前置条件：imageBytes 非空且为有效图片数据，contentType 非空。
     *
     * <p>后置条件：返回符合 RFC 2397 的 Data URI 字符串，供 HTTP 传输使用。
     *
     * @param imageBytes  图片字节数据
     * @param contentType 图片 MIME 类型
     * @return Base64 Data URI
     */
    public static String encodeImage(byte[] imageBytes, @NonNull String contentType) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        return "data:" + contentType + ";base64," + base64;
    }

    /**
     * 调用 CLIP 服务进行批量图片分类。
     *
     * <p>前置条件：images 非空，每个元素为 base64 Data URI 字符串。
     *
     * <p>后置条件：返回 CLIP 服务的分类结果；若服务不可用或响应无法解析则抛出 BusinessException。
     *
     * <p>不变量：不修改传入的 images 列表。
     *
     * @param images Base64 编码的图片列表
     * @return 分类结果列表
     */
    public ClipClassifyResponse classify(@NonNull List<String> images) {
        ClipClassifyRequest request = new ClipClassifyRequest();
        request.setImages(images);

        try {
            ClipClassifyResponse response = restClient
                    .post()
                    .uri("/classify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> handleErrorResponse(res))
                    .body(ClipClassifyResponse.class);

            if (response == null || response.getItems() == null) {
                log.warn("CLIP 服务返回空响应");
                throw new BusinessException(ErrorCodes.AI_OUTPUT_UNAVAILABLE, "AI output is unavailable");
            }

            return response;
        } catch (RestClientException e) {
            log.error("CLIP 服务调用失败: endpoint={}", restClient.toString(), e);
            throw new BusinessException(ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI service is unavailable", e);
        }
    }

    /**
     * 处理 CLIP 服务的错误 HTTP 响应。
     *
     * <p>前置条件：HTTP 响应状态码为 4xx 或 5xx。
     *
     * <p>后置条件：抛出包含适当错误码的 BusinessException。
     *
     * @param response HTTP 客户端响应
     */
    private void handleErrorResponse(ClientHttpResponse response) {
        try {
            int statusCode = response.getStatusCode().value();
            log.warn("CLIP 服务返回错误: status={}", statusCode);
            if (statusCode >= 500) {
                throw new BusinessException(ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI service is unavailable");
            }
            throw new BusinessException(ErrorCodes.AI_OUTPUT_UNAVAILABLE, "AI output is unavailable");
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI service is unavailable");
        }
    }
}

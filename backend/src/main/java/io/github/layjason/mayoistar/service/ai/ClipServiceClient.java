package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.service.ai.ClipModels.ClipClassifyRequest;
import io.github.layjason.mayoistar.service.ai.ClipModels.ClipClassifyResponse;
import java.nio.charset.StandardCharsets;
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
 * <p>类不变量：RestClient 由 ClipClientConfig 创建并注入，包含显式注册的 MappingJackson2HttpMessageConverter。
 */
@Slf4j
@Component
public class ClipServiceClient {

    private final RestClient restClient;

    /**
     * 构造 CLIP 服务客户端。
     *
     * <p>前置条件：clipRestClient 已由 ClipClientConfig 创建，包含 Jackson converter。
     *
     * <p>后置条件：客户端可正确序列化 JSON 请求体并解析响应。
     *
     * @param restClient CLIP 服务 RestClient（由 ClipClientConfig 注入）
     */
    public ClipServiceClient(RestClient restClient) {
        this.restClient = restClient;
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
            log.error("CLIP 服务调用失败", e);
            throw new BusinessException(ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI service is unavailable", e);
        }
    }

    /**
     * 处理 CLIP 服务的错误 HTTP 响应。
     *
     * <p>前置条件：HTTP 响应状态码为 4xx 或 5xx。
     *
     * <p>后置条件：记录响应体详情后抛出包含适当错误码的 BusinessException。
     *
     * @param response HTTP 客户端响应
     */
    private void handleErrorResponse(ClientHttpResponse response) {
        try {
            int statusCode = response.getStatusCode().value();
            String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            log.warn("CLIP 服务返回错误: status={}, body={}", statusCode, responseBody);
            if (statusCode >= 500) {
                throw new BusinessException(ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI service is unavailable");
            }
            throw new BusinessException(ErrorCodes.AI_OUTPUT_UNAVAILABLE, "AI output is unavailable");
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCodes.AI_SERVICE_UNAVAILABLE, "AI service is unavailable");
        }
    }
}

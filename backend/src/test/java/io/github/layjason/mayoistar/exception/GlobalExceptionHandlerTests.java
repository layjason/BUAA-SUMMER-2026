package io.github.layjason.mayoistar.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.api.common.ApiErrorResponse;
import io.github.layjason.mayoistar.api.common.EmptyData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * GlobalExceptionHandler 单元测试。
 *
 * <p>类职责：验证框架层异常（NoResourceFoundException、通用 Exception）返回正确的 HTTP 状态码和统一 JSON 错误响应。
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("资源未找到处理")
    class NoResourceFoundTests {

        /**
         * 验证资源不存在时返回 HTTP 404。
         *
         * <p>前置条件：NoResourceFoundException 被抛出。
         *
         * <p>后置条件：HTTP 状态码为 404，响应体为 code=404 的 ApiErrorResponse。
         */
        @Test
        @DisplayName("应返回 HTTP 404 和 code=404 的错误响应")
        void shouldReturn404WhenResourceNotFound() {
            NoResourceFoundException ex =
                    new NoResourceFoundException(HttpMethod.GET, "No resource found for ", "/nonexistent");

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleNoResourceFound(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(404);
            assertThat(response.getBody().getMessage()).contains("/nonexistent");
        }
    }

    @Nested
    @DisplayName("服务器内部错误处理")
    class InternalServerErrorTests {

        /**
         * 验证未预期异常时返回 HTTP 500。
         *
         * <p>前置条件：任意未在其他处理器中匹配的 Exception 被抛出。
         *
         * <p>后置条件：HTTP 状态码为 500，响应体为 code=500、message 为固定文案的 ApiErrorResponse。
         */
        @Test
        @DisplayName("应返回 HTTP 500 和 code=500 的错误响应")
        void shouldReturn500WhenUnexpectedException() {
            RuntimeException ex = new RuntimeException("Unexpected error");

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(500);
            assertThat(response.getBody().getMessage()).isEqualTo("An internal server error has occurred");
        }
    }

    @Nested
    @DisplayName("业务异常处理（保持 HTTP 200 约定）")
    class BusinessExceptionTests {

        /**
         * 验证业务异常仍返回 HTTP 200。
         *
         * <p>前置条件：BusinessException 被抛出，code=40100。
         *
         * <p>后置条件：HTTP 状态码为 200，响应体为对应 code 和 message 的 ApiErrorResponse。
         */
        @Test
        @DisplayName("应返回 HTTP 200 和业务错误码")
        void shouldReturn200WhenBusinessException() {
            BusinessException ex = new BusinessException(40100, "Test business error");

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleBusinessException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(40100);
            assertThat(response.getBody().getMessage()).isEqualTo("Test business error");
        }
    }
}

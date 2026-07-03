package io.github.layjason.mayoistar.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.api.common.ApiErrorResponse;
import io.github.layjason.mayoistar.api.common.EmptyData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

/**
 * SecurityErrorHandler 单元测试。
 *
 * <p>类职责：验证认证失败和授权失败时返回正确的 HTTP 状态码和统一 JSON 错误响应。
 */
@DisplayName("SecurityErrorHandler")
class SecurityErrorHandlerTest {

    private SecurityErrorHandler handler;
    private ObjectMapper objectMapper;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ByteArrayOutputStream responseOutputStream;
    private HandlerMapping handlerMapping;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        handlerMapping = mock(HandlerMapping.class);
        handler = new SecurityErrorHandler(objectMapper, handlerMapping);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseOutputStream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(responseOutputStream);
        when(response.getOutputStream()).thenReturn(new DelegatingServletOutputStream(responseOutputStream));
        when(response.getWriter()).thenReturn(writer);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");
    }

    @Nested
    @DisplayName("未认证处理")
    class AuthenticationEntryPointTests {

        /**
         * 验证已存在 Controller 处理器的路径未认证时返回 HTTP 401。
         *
         * <p>前置条件：AuthenticationException 被抛出，且请求路径存在已注册的 Controller 处理器。
         *
         * <p>后置条件：HTTP 状态码为 401，响应体为 code=401 的 ApiErrorResponse。
         */
        @Test
        @DisplayName("存在对应处理器时，应返回 HTTP 401")
        void shouldReturn401WhenAuthenticatedAndHandlerExists() throws Exception {
            when(handlerMapping.getHandler(request)).thenReturn(mock(HandlerExecutionChain.class));
            AuthenticationException authException = new AuthenticationException("test auth error") {};

            handler.commence(request, response, authException);

            assertThat(responseOutputStream.toString()).isNotEmpty();
            @SuppressWarnings("unchecked")
            ApiErrorResponse<EmptyData> body =
                    objectMapper.readValue(responseOutputStream.toString(), ApiErrorResponse.class);
            assertThat(body.getCode()).isEqualTo(401);
            assertThat(body.getMessage()).isEqualTo("Authentication is required");
        }

        /**
         * 验证不存在 Controller 处理器的路径未认证时返回 HTTP 404。
         *
         * <p>前置条件：AuthenticationException 被抛出，且请求路径无已注册的 Controller 处理器。
         *
         * <p>后置条件：HTTP 状态码为 404，响应体为 code=404 的 ApiErrorResponse。
         */
        @Test
        @DisplayName("无对应处理器时，应返回 HTTP 404")
        void shouldReturn404WhenNoHandlerFound() throws Exception {
            when(handlerMapping.getHandler(request)).thenReturn(null);
            AuthenticationException authException = new AuthenticationException("test auth error") {};

            handler.commence(request, response, authException);

            assertThat(responseOutputStream.toString()).isNotEmpty();
            @SuppressWarnings("unchecked")
            ApiErrorResponse<EmptyData> body =
                    objectMapper.readValue(responseOutputStream.toString(), ApiErrorResponse.class);
            assertThat(body.getCode()).isEqualTo(404);
            assertThat(body.getMessage()).isEqualTo("Resource not found");
        }

        /**
         * 验证 handlerMapping 为 null 时（测试环境或降级场景）仍返回 HTTP 401。
         *
         * <p>前置条件：AuthenticationException 被抛出，且 handlerMapping 为 null。
         *
         * <p>后置条件：HTTP 状态码为 401。
         */
        @Test
        @DisplayName("handlerMapping 为 null 时，应降级返回 HTTP 401")
        void shouldReturn401WhenHandlerMappingIsNull() throws Exception {
            SecurityErrorHandler nullHandlerMappingHandler = new SecurityErrorHandler(objectMapper, null);
            AuthenticationException authException = new AuthenticationException("test auth error") {};

            nullHandlerMappingHandler.commence(request, response, authException);

            assertThat(responseOutputStream.toString()).isNotEmpty();
            @SuppressWarnings("unchecked")
            ApiErrorResponse<EmptyData> body =
                    objectMapper.readValue(responseOutputStream.toString(), ApiErrorResponse.class);
            assertThat(body.getCode()).isEqualTo(401);
        }

        /**
         * 验证获取处理器抛出异常时降级返回 HTTP 401。
         *
         * <p>前置条件：AuthenticationException 被抛出，handlerMapping.getHandler 抛出异常。
         *
         * <p>后置条件：HTTP 状态码为 401。
         */
        @Test
        @DisplayName("获取处理器异常时，应降级返回 HTTP 401")
        void shouldReturn401WhenGetHandlerThrowsException() throws Exception {
            when(handlerMapping.getHandler(request)).thenThrow(new RuntimeException("internal error"));
            AuthenticationException authException = new AuthenticationException("test auth error") {};

            handler.commence(request, response, authException);

            assertThat(responseOutputStream.toString()).isNotEmpty();
            @SuppressWarnings("unchecked")
            ApiErrorResponse<EmptyData> body =
                    objectMapper.readValue(responseOutputStream.toString(), ApiErrorResponse.class);
            assertThat(body.getCode()).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("无权限处理")
    class AccessDeniedHandlerTests {

        /**
         * 验证无权限时返回 HTTP 403。
         *
         * <p>前置条件：AccessDeniedException 被抛出。
         *
         * <p>后置条件：HTTP 状态码为 403，响应体为 code=403 的 ApiErrorResponse。
         */
        @Test
        @DisplayName("应返回 HTTP 403 和 code=403 的错误响应")
        void shouldReturn403WhenAccessDenied() throws Exception {
            AccessDeniedException accessDeniedException = new AccessDeniedException("test access denied");

            handler.handle(request, response, accessDeniedException);

            assertThat(responseOutputStream.toString()).isNotEmpty();
            @SuppressWarnings("unchecked")
            ApiErrorResponse<EmptyData> body =
                    objectMapper.readValue(responseOutputStream.toString(), ApiErrorResponse.class);
            assertThat(body.getCode()).isEqualTo(403);
            assertThat(body.getMessage()).isEqualTo("Permission is denied");
        }
    }

    /**
     * ServletOutputStream 的代理实现，将写入数据重定向到 ByteArrayOutputStream。
     */
    private static class DelegatingServletOutputStream extends jakarta.servlet.ServletOutputStream {

        private final ByteArrayOutputStream target;

        DelegatingServletOutputStream(ByteArrayOutputStream target) {
            this.target = target;
        }

        @Override
        public void write(int b) throws IOException {
            target.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(jakarta.servlet.WriteListener listener) {
            // 同步写入，无需回调
        }
    }
}

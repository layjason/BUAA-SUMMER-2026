package io.github.layjason.mayoistar.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.common.ApiErrorResponse;
import io.github.layjason.mayoistar.api.common.EmptyData;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * GlobalExceptionHandler 单元测试。
 *
 * <p>类职责：验证框架层异常（NoHandlerFoundException、NoResourceFoundException、校验异常、通用 Exception）返回正确的 HTTP 状态码和统一 JSON 错误响应。
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
    @DisplayName("Controller 处理器未找到处理")
    class NoHandlerFoundTests {

        /**
         * 验证 Controller 不存在时返回 HTTP 404。
         *
         * <p>前置条件：DispatcherServlet 抛出 NoHandlerFoundException。
         *
         * <p>后置条件：HTTP 状态码为 404，响应体为 code=404、message 含有请求方法和路径的 ApiErrorResponse。
         */
        @Test
        @DisplayName("应返回 HTTP 404 和 code=404 的错误响应")
        void shouldReturn404WhenControllerNotMapped() {
            NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/chat/conversation/messages", null);

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleNoHandlerFound(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(404);
            assertThat(response.getBody().getMessage()).contains("GET");
            assertThat(response.getBody().getMessage()).contains("/chat/conversation/messages");
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

    @Nested
    @DisplayName("HTTP 方法不支持处理")
    class HttpRequestMethodNotSupportedTests {

        /**
         * 验证不支持的 HTTP 方法时返回 HTTP 405。
         *
         * <p>前置条件：HttpRequestMethodNotSupportedException 被抛出，请求方法为 POST，支持 GET, HEAD。
         *
         * <p>后置条件：HTTP 状态码为 405，响应体为 code=405 的 ApiErrorResponse，message 包含请求和支持的方法。
         */
        @Test
        @DisplayName("应返回 HTTP 405 和 code=405 的错误响应")
        void shouldReturn405WhenMethodNotSupported() {
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("POST", List.of("GET", "HEAD"));

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleHttpRequestMethodNotSupported(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(405);
            assertThat(response.getBody().getMessage()).contains("POST");
            assertThat(response.getBody().getMessage()).contains("GET");
        }

        /**
         * 验证不支持的 HTTP 方法异常中 supportedHttpMethods 为空时不抛出异常。
         *
         * <p>前置条件：HttpRequestMethodNotSupportedException 由单参构造器创建，getSupportedHttpMethods() 返回空集合。
         *
         * <p>后置条件：HTTP 状态码为 405，响应体 message 包含 "未知" 作为支持方法列表。
         */
        @Test
        @DisplayName("应正确处理 supportedHttpMethods 为空的情况")
        void shouldHandleEmptySupportedMethods() {
            HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleHttpRequestMethodNotSupported(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(405);
            assertThat(response.getBody().getMessage()).contains("POST");
            assertThat(response.getBody().getMessage()).contains("未知");
        }
    }

    @Nested
    @DisplayName("请求体参数校验失败处理")
    class MethodArgumentNotValidTests {

        /**
         * 验证多个字段校验失败时 message 包含所有字段名和错误。
         *
         * <p>前置条件：MethodArgumentNotValidException 包含多个字段的校验错误。
         *
         * <p>后置条件：HTTP 200、code=400、message 包含所有字段名和错误、data 为空对象。
         */
        @Test
        @DisplayName("应返回所有字段校验错误及字段名")
        void shouldReturnAllFieldErrors() {
            Object target = new Object();
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "teamCreateRequest");
            bindingResult.addError(new FieldError("teamCreateRequest", "name", "不能为空"));
            bindingResult.addError(new FieldError("teamCreateRequest", "tags", "不能为空"));

            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleMethodArgumentNotValid(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(400);
            assertThat(response.getBody().getMessage()).contains("name");
            assertThat(response.getBody().getMessage()).contains("tags");
            assertThat(response.getBody().getMessage()).contains("不能为空");
        }

        /**
         * 验证无字段校验错误时返回默认消息。
         *
         * <p>前置条件：MethodArgumentNotValidException 的 BindingResult 不包含任何字段错误。
         *
         * <p>后置条件：message 为 "请求参数校验失败"。
         */
        @Test
        @DisplayName("应正确处理无字段错误的情况")
        void shouldHandleNoFieldErrors() {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleMethodArgumentNotValid(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(400);
            assertThat(response.getBody().getMessage()).isEqualTo("请求参数校验失败");
        }
    }

    @Nested
    @DisplayName("方法参数校验失败处理")
    class ConstraintViolationTests {

        /**
         * 验证多个方法参数校验失败时 message 包含字段叶子名和错误。
         *
         * <p>前置条件：ConstraintViolationException 包含多个约束违反。
         *
         * <p>后置条件：HTTP 200、code=400、message 包含字段叶子名和错误。
         */
        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("应返回所有约束违反及字段名")
        void shouldReturnAllConstraintViolations() {
            Path.Node nameNode = mockPathNode("name");
            Path.Node createTeamNode = mockPathNode("createTeam");
            Path.Node arg0Node = mockPathNode("arg0");
            Path.Node tagsNode = mockPathNode("tags");

            ConstraintViolation<Object> violation1 = mock(ConstraintViolation.class);
            Path path1 = mock(Path.class);
            when(path1.spliterator()).thenReturn(List.of(nameNode).spliterator());
            when(violation1.getPropertyPath()).thenReturn(path1);
            when(violation1.getMessage()).thenReturn("不能为空");

            ConstraintViolation<Object> violation2 = mock(ConstraintViolation.class);
            Path path2 = mock(Path.class);
            when(path2.spliterator())
                    .thenReturn(List.of(createTeamNode, arg0Node, tagsNode).spliterator());
            when(violation2.getPropertyPath()).thenReturn(path2);
            when(violation2.getMessage()).thenReturn("不能为空");

            Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();
            violations.add((ConstraintViolation<?>) violation1);
            violations.add((ConstraintViolation<?>) violation2);
            ConstraintViolationException ex = new ConstraintViolationException("message", violations);

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleConstraintViolation(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(400);
            assertThat(response.getBody().getMessage()).contains("name");
            assertThat(response.getBody().getMessage()).contains("tags");
            assertThat(response.getBody().getMessage()).contains("不能为空");
        }

        /**
         * 验证无约束违反时返回默认消息。
         *
         * <p>前置条件：ConstraintViolationException 不包含任何约束违反。
         *
         * <p>后置条件：message 为 "请求参数校验失败"。
         */
        @Test
        @DisplayName("应正确处理无约束违反的情况")
        void shouldHandleNoViolations() {
            ConstraintViolationException ex = new ConstraintViolationException("message", Set.of());

            ResponseEntity<ApiErrorResponse<EmptyData>> response = handler.handleConstraintViolation(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(400);
            assertThat(response.getBody().getMessage()).isEqualTo("请求参数校验失败");
        }

        private Path.Node mockPathNode(String name) {
            Path.Node node = mock(Path.Node.class);
            when(node.getName()).thenReturn(name);
            return node;
        }
    }
}

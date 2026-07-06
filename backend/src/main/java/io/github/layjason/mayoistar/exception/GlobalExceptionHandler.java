package io.github.layjason.mayoistar.exception;

import io.github.layjason.mayoistar.api.common.ApiErrorResponse;
import io.github.layjason.mayoistar.api.common.EmptyData;
import io.github.layjason.mayoistar.api.common.ValidationError;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * <p>类职责：将业务异常和校验异常转换为符合 API 约定的错误响应。
 *
 * <p>类不变量：
 * <ul>
 *   <li>业务逻辑错误的 HTTP 状态码固定为 200，错误码通过 ApiErrorResponse.code 传递。</li>
 *   <li>基础设施/框架层错误（404/500）返回对应的 HTTP 状态码，响应体同样使用 ApiErrorResponse 格式。</li>
 * </ul>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * <p>前置条件：Controller 或 Service 抛出 BusinessException。
     *
     * <p>后置条件：返回 code + message 匹配异常中错误码的 API 错误响应，HTTP 200。
     *
     * @param ex 业务异常
     * @return 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getBusinessMessage());
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>();
        body.setCode(ex.getCode());
        body.setMessage(ex.getBusinessMessage());
        body.setData(new EmptyData());
        return ResponseEntity.ok(body);
    }

    /**
     * 处理请求体参数校验失败异常。
     *
     * <p>前置条件：Controller 的 @Valid 触发 Jakarta Bean Validation 失败。
     *
     * <p>后置条件：返回 code=400、message 为所有校验错误汇总（含字段名）的 API 错误响应，HTTP 200。
     *
     * @param ex 校验异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ValidationError(e.getField(), e.getDefaultMessage()))
                .toList();
        String message = errors.isEmpty()
                ? "请求参数校验失败"
                : errors.stream().map(e -> e.getField() + "：" + e.getMessage()).collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", message);
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>();
        body.setCode(400);
        body.setMessage(message);
        body.setData(new EmptyData());
        return ResponseEntity.ok(body);
    }

    /**
     * 处理方法参数校验失败异常。
     *
     * <p>前置条件：@Validated 触发方法级参数校验失败。
     *
     * <p>后置条件：返回 code=400、message 为所有校验错误汇总（含字段名）的 API 错误响应，HTTP 200。
     *
     * @param ex 校验异常
     * @return 错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ValidationError> errors = ex.getConstraintViolations().stream()
                .map(v -> {
                    String field = StreamSupport.stream(v.getPropertyPath().spliterator(), false)
                            .reduce((first, second) -> second)
                            .map(Path.Node::getName)
                            .orElse("unknown");
                    return new ValidationError(field, v.getMessage());
                })
                .toList();
        String message = errors.isEmpty()
                ? "请求参数校验失败"
                : errors.stream().map(e -> e.getField() + "：" + e.getMessage()).collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", message);
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>();
        body.setCode(400);
        body.setMessage(message);
        body.setData(new EmptyData());
        return ResponseEntity.ok(body);
    }

    /**
     * 处理资源不存在的异常。
     *
     * <p>前置条件：DispatcherServlet 未找到匹配的处理器映射，NoResourceFoundException 被抛出。
     *
     * <p>后置条件：返回 code=404、message 为异常消息的 API 错误响应，HTTP 404。
     *
     * @param ex 资源不存在异常
     * @return 错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("资源未找到: {}", ex.getMessage());
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>();
        body.setCode(404);
        body.setMessage(ex.getMessage());
        body.setData(new EmptyData());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * 处理不支持的 HTTP 方法异常。
     *
     * <p>前置条件：客户端使用未注册的 HTTP 方法访问已存在的路径。
     *
     * <p>后置条件：返回 code=405、message 包含请求方法和支持方法的 API 错误响应，HTTP 405。
     *
     * @param ex HTTP 方法不支持异常
     * @return 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        String supportedMethods = ex.getSupportedHttpMethods() != null
                        && !ex.getSupportedHttpMethods().isEmpty()
                ? ex.getSupportedHttpMethods().stream().map(HttpMethod::name).collect(Collectors.joining(", "))
                : "未知";
        String message = "HTTP 方法 " + ex.getMethod() + " 不受支持，支持以下方法: " + supportedMethods;
        log.warn("HTTP 方法不支持: {}", message);
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>();
        body.setCode(405);
        body.setMessage(message);
        body.setData(new EmptyData());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    /**
     * 处理带 HTTP 状态码的应用异常。
     *
     * <p>前置条件：Controller 或 Service 抛出 ResponseStatusException。
     *
     * <p>后置条件：返回异常指定的 HTTP 状态码，响应体使用同状态码 code 和异常 reason。
     *
     * @param ex HTTP 状态异常
     * @return 错误响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleResponseStatusException(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        log.warn("HTTP 状态异常: status={}, message={}", status, message);
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>();
        body.setCode(status);
        body.setMessage(message);
        body.setData(new EmptyData());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    /**
     * 处理未预期的服务器内部异常（兜底处理器）。
     *
     * <p>前置条件：Controller 层抛出任何未在前面处理器中匹配的异常。
     *
     * <p>后置条件：返回 code=500、message 为固定英文文案的 API 错误响应，HTTP 500。
     *
     * @param ex 未预期的异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleException(Exception ex) {
        log.error("服务器内部错误", ex);
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>();
        body.setCode(500);
        body.setMessage("An internal server error has occurred");
        body.setData(new EmptyData());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

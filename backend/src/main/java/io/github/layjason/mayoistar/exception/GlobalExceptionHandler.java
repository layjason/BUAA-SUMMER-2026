package io.github.layjason.mayoistar.exception;

import io.github.layjason.mayoistar.api.common.ApiErrorResponse;
import io.github.layjason.mayoistar.api.common.EmptyData;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
     * <p>后置条件：返回 code=400，message 为第一条校验失败信息的 API 错误响应，HTTP 200。
     *
     * @param ex 校验异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        var fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数校验失败";
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
     * <p>后置条件：返回 code=400，message 为第一条校验失败信息的 API 错误响应，HTTP 200。
     *
     * @param ex 校验异常
     * @return 错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleConstraintViolation(ConstraintViolationException ex) {
        var violation = ex.getConstraintViolations().iterator().next();
        String message = violation != null ? violation.getMessage() : "请求参数校验失败";
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

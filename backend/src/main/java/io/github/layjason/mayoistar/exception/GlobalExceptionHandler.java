package io.github.layjason.mayoistar.exception;

import io.github.layjason.mayoistar.api.common.ApiErrorResponse;
import io.github.layjason.mayoistar.api.common.EmptyData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 全局异常处理器。
 *
 * <p>类职责：将业务异常转换为符合 API 约定的错误响应。
 *
 * <p>类不变量：所有错误响应的 HTTP 状态码固定为 200，错误码通过 ApiErrorResponse.code 传递。
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
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getBusinessMessage(), ex);
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>();
        body.setCode(ex.getCode());
        body.setMessage(ex.getBusinessMessage());
        body.setData(new EmptyData());
        return ResponseEntity.ok(body);
    }
}

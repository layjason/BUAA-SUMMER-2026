package io.github.layjason.mayoistar.api.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 全局异常处理器。
 *
 * <p>类职责：将业务异常转换为符合 TypeSpec APIResult 规范的 HTTP 200 错误响应，确保所有 JSON
 * 接口统一返回 HTTP 200，错误信息通过响应体中的 code 和 message 字段表达。
 *
 * <p>类不变量：不吞没异常，不直接访问数据库或外部服务。
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * <p>前置条件：exception 包含符合 errors.tsp 定义的 businessCode。
     *
     * <p>后置条件：返回 HTTP 200，响应体为 ApiErrorResponse，data 为空对象。
     *
     * <p>不变量：该方法不修改异常信息，不做业务判断。
     *
     * @param exception 业务异常
     * @return HTTP 200 的错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse<EmptyData>> handleBusinessException(BusinessException exception) {
        log.debug("业务异常，businessCode={}, message={}", exception.getBusinessCode(), exception.getMessage());
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>();
        body.setCode(exception.getBusinessCode());
        body.setMessage(exception.getMessage());
        body.setData(new EmptyData());
        return ResponseEntity.ok(body);
    }
}

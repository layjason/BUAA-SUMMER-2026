package io.github.layjason.mayoistar.api.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>类职责：携带业务错误码的运行时异常，由全局异常处理器统一转换为 ApiErrorResponse，确保
 * 所有业务接口均返回 HTTP 200。
 *
 * <p>类不变量：businessCode 不为 null，message 非空。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int businessCode;

    /**
     * 创建业务异常。
     *
     * <p>前置条件：businessCode 语义与 TypeSpec errors.tsp 定义一致，message 为人类可读的错误描述。
     *
     * <p>后置条件：异常包含业务错误码和消息，可被全局异常处理器捕获。
     *
     * @param businessCode TypeSpec 中定义的业务错误码
     * @param message 错误消息
     */
    public BusinessException(int businessCode, String message) {
        super(message);
        this.businessCode = businessCode;
    }
}

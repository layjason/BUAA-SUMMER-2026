package io.github.layjason.mayoistar.exception;

import lombok.Getter;

/**
 * 业务异常，携带错误码和英文模板消息。
 *
 * <p>类职责：表示业务规则不满足时抛出的异常，由全局异常处理器转换为 API 错误响应。
 *
 * <p>类不变量：code 为 >=10000 的业务错误码，message 为英文模板消息。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String businessMessage;

    /**
     * @param code             业务错误码
     * @param businessMessage  英文模板消息，用于 API 响应
     */
    public BusinessException(int code, String businessMessage) {
        super(businessMessage);
        this.code = code;
        this.businessMessage = businessMessage;
    }

    /**
     * @param code             业务错误码
     * @param businessMessage  英文模板消息
     * @param cause            原始异常
     */
    public BusinessException(int code, String businessMessage, Throwable cause) {
        super(businessMessage, cause);
        this.code = code;
        this.businessMessage = businessMessage;
    }
}

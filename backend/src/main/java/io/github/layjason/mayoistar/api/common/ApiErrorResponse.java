package io.github.layjason.mayoistar.api.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台统一 JSON 错误响应。
 *
 * <p>类职责：表达 TypeSpec 中 APIErrorResponse 的错误响应包装结构。
 *
 * <p>类不变量：code 与 message 由具体错误类型决定，data 承载错误上下文。
 *
 * @param <ErrorData> 错误上下文类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse<ErrorData> {

    private Integer code;

    private String message;

    private ErrorData data;
}

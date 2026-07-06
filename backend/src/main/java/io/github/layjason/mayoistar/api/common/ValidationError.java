package io.github.layjason.mayoistar.api.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段校验错误。
 *
 * <p>类职责：描述单个字段的参数校验失败详情，包含字段名和校验失败消息。
 *
 * <p>类不变量：field 为校验失败的字段路径，message 为人类可读的校验失败原因。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {

    private String field;

    private String message;
}

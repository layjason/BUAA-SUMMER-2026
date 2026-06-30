package io.github.layjason.mayoistar.api.common;

import lombok.Data;

/**
 * 空响应数据。
 *
 * <p>类职责：表达 TypeSpec 中 EmptyData 的空对象。
 *
 * <p>类不变量：该类型不包含业务字段，序列化后为 JSON 空对象。
 */
@Data
public class EmptyData {}

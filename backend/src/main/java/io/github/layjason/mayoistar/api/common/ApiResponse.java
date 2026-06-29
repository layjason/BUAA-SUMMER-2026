package io.github.layjason.mayoistar.api.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台统一 JSON 响应。
 *
 * <p>类职责：表达 TypeSpec 中 APIResponse 的响应包装结构。
 *
 * <p>类不变量：成功响应的 code 固定为 200，message 固定为 For Super Earth!。
 *
 * @param <ResponseData> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<ResponseData> {

    private Integer code;

    private String message;

    private ResponseData data;

    /**
     * 创建契约成功响应。
     *
     * <p>前置条件：data 已按对应接口响应模型构造，可被 JSON 序列化。
     *
     * <p>后置条件：返回 code 为 200、message 为 For Super Earth! 的统一响应。
     *
     * <p>不变量：该方法不访问数据库，不修改外部状态。
     *
     * @param data 响应数据
     * @param <ResponseData> 响应数据类型
     * @return 统一成功响应
     */
    public static <ResponseData> ApiResponse<ResponseData> success(ResponseData data) {
        return new ApiResponse<>(200, "For Super Earth!", data);
    }
}

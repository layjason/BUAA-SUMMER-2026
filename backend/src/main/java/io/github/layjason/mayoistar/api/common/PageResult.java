package io.github.layjason.mayoistar.api.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页响应数据。
 *
 * <p>类职责：表达 TypeSpec 中 PageResult 的分页包装结构。
 *
 * <p>类不变量：items 不为 null，page 与 pageSize 为正数，totalPages 表示总页数。
 *
 * @param <Item> 分页条目类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<Item> {

    private List<Item> items;

    private Long total;

    private Integer page;

    private Integer pageSize;

    private Integer totalPages;

    /**
     * 创建空分页结果。
     *
     * <p>前置条件：调用方仅需要契约合法的占位分页结果。
     *
     * <p>后置条件：返回第 1 页、每页 20 条、无数据的分页对象。
     *
     * <p>不变量：该方法不访问数据库，不修改外部状态。
     *
     * @param <Item> 分页条目类型
     * @return 空分页结果
     */
    public static <Item> PageResult<Item> empty() {
        return new PageResult<>(List.of(), 0L, 1, 20, 0);
    }
}

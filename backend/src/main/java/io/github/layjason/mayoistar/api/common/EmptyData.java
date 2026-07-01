package io.github.layjason.mayoistar.api.common;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.Map;
import lombok.Data;

/**
 * 空响应数据。
 *
 * <p>类职责：表达 TypeSpec 中 EmptyData 的空对象。
 *
 * <p>类不变量：该类型不包含业务字段，序列化后为 JSON 空对象。
 */
@Data
public class EmptyData {

    /**
     * 提供空对象序列化入口。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：Jackson 将 EmptyData 序列化为 JSON 空对象 {}，不会因空 Bean 失败。
     *
     * <p>不变量：返回值始终为空 Map，不承载业务字段。
     *
     * @return 空属性集合
     */
    @JsonAnyGetter
    public Map<String, Object> properties() {
        return Map.of();
    }
}

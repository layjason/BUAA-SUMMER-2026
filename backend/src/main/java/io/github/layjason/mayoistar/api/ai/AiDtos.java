package io.github.layjason.mayoistar.api.ai;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * AI 辅助接口 DTO 占位集合。
 *
 * <p>类职责：提供与 TypeSpec AI 接口请求模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class AiDtos {

    /**
     * 阻止实例化 DTO 命名空间类。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：外部无法创建该工具型容器类实例。
     *
     * <p>不变量：该构造函数不创建任何 DTO 对象。
     */
    private AiDtos() {}

    @Data
    public static class ActivityPlanningRequest {
        @NotNull
        private String topic;

        private String activityType;
        private String city;
        private Integer expectedParticipants;
        private String additionalRequirements;
    }

    @Data
    public static class ImageClassificationRequest {
        @NotNull
        private List<String> mediaIds;
    }
}

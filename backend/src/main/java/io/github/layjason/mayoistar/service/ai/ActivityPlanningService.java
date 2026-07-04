package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos;

/**
 * AI 活动策划业务服务。
 *
 * <p>类职责：根据用户输入生成结构化活动策划草案。
 */
public interface ActivityPlanningService {

    /**
     * 生成活动策划草案。
     *
     * <p>前置条件：request 已通过基础参数校验。
     *
     * <p>后置条件：返回符合 API 契约的策划结果；模型不可用时返回 failed 状态。
     *
     * @param request 活动策划请求
     * @return 活动策划结果
     */
    AiDtos.ActivityPlanningResult generateActivityPlan(AiDtos.ActivityPlanningRequest request);
}

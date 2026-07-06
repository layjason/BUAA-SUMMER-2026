package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos;

/**
 * AI 活动策划业务服务。
 *
 * <p>类职责：根据用户输入生成可编辑的结构化活动草案。
 *
 * <p>类不变量：服务层只返回契约 DTO，不暴露模型供应商响应结构。
 */
public interface ActivityPlanningService {

    /**
     * 生成活动策划草案。
     *
     * <p>前置条件：request 非空且已通过 Controller 参数校验。
     *
     * <p>后置条件：成功时返回 status=succeeded 的结构化活动策划结果；失败时抛出 AI 业务异常。
     *
     * <p>不变量：不保存用户输入和模型原文。
     *
     * @param request 活动策划请求
     * @return 活动策划结果
     */
    AiDtos.ActivityPlanningResult generateActivityPlan(AiDtos.ActivityPlanningRequest request);
}

package io.github.layjason.mayoistar.service.ai;

/**
 * AI 活动策划模型客户端。
 *
 * <p>类职责：抽象具体大模型供应商调用，供业务服务生成结构化活动草案。
 */
public interface ActivityPlanningClient {

    /**
     * 根据提示词生成活动策划草案。
     *
     * <p>前置条件：prompt 已包含输出 JSON 结构约束，不包含供应商凭据。
     *
     * <p>后置条件：返回模型输出文本；若供应商未配置或不可用，返回失败结果。
     *
     * <p>不变量：实现不得记录 API Token。
     *
     * @param prompt 大模型提示词
     * @return 模型调用结果
     */
    ActivityPlanningClientResult generate(String prompt);
}

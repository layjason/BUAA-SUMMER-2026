package io.github.layjason.mayoistar.service.ai;

/**
 * AI 活动策划客户端。
 *
 * <p>类职责：封装具体大模型供应商调用，使业务服务无需感知 HTTP 契约。
 *
 * <p>类不变量：实现类不得记录 API Token 或完整用户提示词。
 */
public interface ActivityPlanningClient {

    /**
     * 根据提示词调用活动策划模型。
     *
     * <p>前置条件：prompt 非空且已包含输出格式约束。
     *
     * <p>后置条件：成功时返回模型原始文本内容；失败时抛出契约业务异常。
     *
     * <p>不变量：该方法不持久化用户输入。
     *
     * @param prompt 活动策划提示词
     * @return 模型原始响应文本
     */
    String generate(String prompt);
}

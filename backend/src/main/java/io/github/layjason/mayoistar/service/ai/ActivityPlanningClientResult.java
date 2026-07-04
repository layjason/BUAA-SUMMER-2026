package io.github.layjason.mayoistar.service.ai;

/**
 * AI 活动策划模型调用结果。
 *
 * @param succeeded 是否调用成功
 * @param content 模型返回内容
 * @param friendlyErrorMessage 可展示给用户的友好错误
 */
public record ActivityPlanningClientResult(boolean succeeded, String content, String friendlyErrorMessage) {

    public static ActivityPlanningClientResult succeeded(String content) {
        return new ActivityPlanningClientResult(true, content, null);
    }

    public static ActivityPlanningClientResult failed(String friendlyErrorMessage) {
        return new ActivityPlanningClientResult(false, null, friendlyErrorMessage);
    }
}

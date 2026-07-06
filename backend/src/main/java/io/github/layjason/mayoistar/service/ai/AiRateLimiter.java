package io.github.layjason.mayoistar.service.ai;

/**
 * AI 调用频率限制器。
 *
 * <p>类职责：按用户与操作维度限制 AI 接口调用频率。
 *
 * <p>类不变量：实现类必须以原子方式判断并记录调用次数。
 */
public interface AiRateLimiter {

    /**
     * 尝试消费一次 AI 调用额度。
     *
     * <p>前置条件：userId 与 operation 非空。
     *
     * <p>后置条件：若仍有额度则记录一次调用并返回 true；否则不增加调用次数并返回 false。
     *
     * <p>不变量：不同用户或不同操作的计数相互隔离。
     *
     * @param userId 当前登录用户标识
     * @param operation AI 操作名称
     * @return 是否成功消费调用额度
     */
    boolean tryAcquire(String userId, String operation);
}

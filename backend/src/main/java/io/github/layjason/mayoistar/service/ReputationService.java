package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;

/**
 * 信誉积分服务接口。
 *
 * <p>类职责：定义信誉积分变更记录与积分累加操作。仅保存变更记录和简单累加，不实现计算策略。
 */
public interface ReputationService {

    /**
     * 记录积分变更并更新用户当前信誉分。
     *
     * <p>前置条件：userId 对应有效用户。scoreChange 可为正数（加分）或负数（扣分）。
     *
     * <p>后置条件：ReputationRecord 已持久化，PersonalProfile.reputationScore 已原子更新。
     *
     * @param userId      用户 ID
     * @param scoreChange 积分变动量
     * @param source      变更来源
     * @param referenceId 关联实体 ID（如举报 ID），可为 null
     * @param reason      变更原因
     */
    void recordScoreChange(
            String userId, int scoreChange, ReputationChangeSource source, String referenceId, String reason);
}

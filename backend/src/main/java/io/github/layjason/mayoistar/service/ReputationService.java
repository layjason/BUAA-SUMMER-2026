package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;
import org.springframework.lang.Nullable;

/**
 * 信誉积分服务接口。
 *
 * <p>类职责：按已处理举报重新计算用户当前信誉分，并保存信誉积分变更流水用于审计。
 *
 * <p>不变量：当前信誉分由举报事实重新计算得到，不以流水累加结果作为唯一依据。
 */
public interface ReputationService {

    /**
     * 禁止报名活动的信誉分阈值。
     */
    int ACTIVITY_BAN_THRESHOLD = 60;

    /**
     * 初始信誉分。
     */
    int DEFAULT_SCORE = 100;

    /**
     * 重新计算并更新指定用户的信誉分。
     *
     * <p>前置条件：userId 对应用户存在且有 PersonalProfile。
     *
     * <p>后置条件：PersonalProfile.reputationScore 更新为按已处理举报计算后的值，且不低于 0。
     *
     * @param userId 用户 ID
     * @return 更新后的信誉分
     */
    int recalculateScore(String userId);

    /**
     * 获取用户当前信誉分。
     *
     * <p>前置条件：userId 对应用户可以没有 PersonalProfile。
     *
     * <p>后置条件：返回当前信誉分，若未设置或资料不存在则返回默认值。
     *
     * @param userId 用户 ID
     * @return 当前信誉分
     */
    int getCurrentScore(String userId);

    /**
     * 检查用户信誉分是否允许报名活动。
     *
     * <p>前置条件：userId 对应用户可以没有 PersonalProfile。
     *
     * <p>后置条件：返回 true 表示当前信誉分达到报名阈值。
     *
     * @param userId 用户 ID
     * @return 是否允许报名活动
     */
    boolean canRegisterForActivity(String userId);

    /**
     * 保存积分变更流水。
     *
     * <p>前置条件：userId 对应有效用户。scoreChange 表示本次事实变化导致的净分值变化。
     *
     * <p>后置条件：若 source 和 referenceId 尚无流水，则 ReputationRecord 已持久化；当前信誉分不会被流水累加修改。
     *
     * @param userId      用户 ID
     * @param scoreChange 积分变动量
     * @param source      变更来源
     * @param referenceId 关联实体 ID（如举报 ID），可为 null
     * @param reason      变更原因
     */
    void recordScoreChange(
            String userId, int scoreChange, ReputationChangeSource source, @Nullable String referenceId, String reason);
}

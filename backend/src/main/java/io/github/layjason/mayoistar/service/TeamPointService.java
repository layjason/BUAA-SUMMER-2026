package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.entity.social.TeamPointChangeSource;
import org.springframework.lang.Nullable;

/**
 * 小队积分服务接口。
 *
 * <p>类职责：提供积分增减、积分变动历史查询等功能。
 *
 * <p>不变量：成员积分不低于 0；每条积分变动 (source, referenceId) 唯一。
 */
public interface TeamPointService {

    int CHECK_IN_POINTS = 10;
    int NO_SHOW_POINTS = -5;
    int SUMMARY_POST_POINTS = 5;

    /**
     * 为指定小队成员增减积分。
     *
     * <p>前置条件：成员在指定小队内；当扣分时，积分不低于 0 且 pointChange 为负数。
     *
     * <p>后置条件：成员积分已更新，积分变动流水已写入；若 (source, referenceId) 已有记录则跳过。
     *
     * @param teamId      小队 ID
     * @param userId      用户 ID
     * @param pointChange 积分变动量（正数为加，负数为减）
     * @param source      变动来源
     * @param referenceId 关联实体 ID（可为 null）
     * @param reason      变动原因
     */
    void addPoints(
            String teamId, String userId, int pointChange, TeamPointChangeSource source,
            @Nullable String referenceId, String reason);

    /**
     * 批量处理活动爽约扣分。
     *
     * <p>前置条件：activityId 对应活动为小队活动、活动已结束。
     *
     * <p>后置条件：所有报名但未签到的成员积分被扣减 noShow 分。
     *
     * @param activityId 活动 ID
     */
    void processNoShows(String activityId);
}

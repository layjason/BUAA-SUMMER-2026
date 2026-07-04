package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.social.TeamPointChangeSource;
import org.springframework.lang.Nullable;

/**
 * 小队积分服务接口。
 *
 * <p>类职责：提供积分增减、积分变动历史查询、爽约批量扣分等功能。
 *
 * <p>不变量：成员积分不低于 0；每条积分变动 (source, referenceId) 唯一。
 */
public interface TeamPointService {

    int CHECK_IN_POINTS = 10;
    int NO_SHOW_POINTS = -5;
    int SUMMARY_POST_POINTS = 5;

    void addPoints(
            String teamId, String userId, int pointChange, TeamPointChangeSource source,
            @Nullable String referenceId, String reason);

    void processNoShows(String activityId);

    void adjustPoints(String teamId, String userId, int pointChange, String operatorId, String reason);

    /**
     * 查询成员积分变动历史。
     *
     * <p>前置条件：成员在校验成功的小队内。
     *
     * <p>后置条件：返回分页的积分变动记录。
     *
     * @param teamId   小队 ID
     * @param userId   用户 ID
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页积分变动记录
     */
    PageResult<SocialDtos.TeamPointRecordItem> getPointHistory(String teamId, String userId, int page, int pageSize);
}

package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;

/**
 * 关注关系服务。
 *
 * <p>类职责：处理用户关注、取消关注、关注列表与粉丝列表查询。
 */
public interface FollowService {

    /**
     * 关注目标用户。
     *
     * <p>前置条件：followerId 与 followedId 是不同用户，目标用户可见，双方不存在黑名单关系。
     *
     * <p>后置条件：关注关系已创建；若双方互相关注且尚非好友，则创建双向互关好友关系。
     */
    SocialDtos.FollowRelation followUser(String followerId, String followedId);

    /**
     * 取消关注目标用户。
     *
     * <p>前置条件：followerId 已关注 followedId。
     *
     * <p>后置条件：关注关系已删除；若好友关系来源为互关，则双向好友关系已删除。
     */
    SocialDtos.FollowRelation unfollowUser(String followerId, String followedId);

    /**
     * 查询当前用户关注的人。
     *
     * <p>前置条件：page 从 1 开始，pageSize 为正数。
     *
     * <p>后置条件：返回按关注时间倒序排列的分页列表。
     */
    PageResult<SocialDtos.FollowItem> listFollows(String userId, int page, int pageSize);

    /**
     * 查询当前用户的粉丝。
     *
     * <p>前置条件：page 从 1 开始，pageSize 为正数。
     *
     * <p>后置条件：返回按关注时间倒序排列的分页列表。
     */
    PageResult<SocialDtos.FollowItem> listFollowers(String userId, int page, int pageSize);
}

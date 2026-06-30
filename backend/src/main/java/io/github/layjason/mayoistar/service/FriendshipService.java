package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import java.util.List;

/**
 * 好友管理服务接口。
 *
 * <p>类职责：定义好友列表查询、备注更新、分组标签、删除好友操作。
 */
public interface FriendshipService {

    /**
     * 分页查询当前用户的好友列表。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页 FriendItem 列表，含昵称、头像、备注、分组标签。
     *
     * @param userId   当前用户 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 好友分页结果
     */
    PageResult<SocialDtos.FriendItem> listFriends(String userId, int page, int pageSize);

    /**
     * 更新好友的备注和分组标签。
     *
     * <p>前置条件：好友关系存在。
     *
     * <p>后置条件：关系中当前用户一侧的 remark 和 groupTags 已更新。
     *
     * @param userId       当前用户 ID
     * @param friendUserId 好友用户 ID
     * @param remark       新备注（null 表示不修改）
     * @param groupTags    新分组标签（null 表示不修改）
     * @return 更新后的 FriendItem
     */
    SocialDtos.FriendItem updateFriendRemark(String userId, String friendUserId, String remark, List<String> groupTags);

    /**
     * 删除好友，双方的好友关系同时解除。
     *
     * <p>前置条件：好友关系存在。
     *
     * <p>后置条件：双向 Friendship 记录均已删除。
     *
     * @param userId       当前用户 ID
     * @param friendUserId 好友用户 ID
     */
    void deleteFriend(String userId, String friendUserId);
}

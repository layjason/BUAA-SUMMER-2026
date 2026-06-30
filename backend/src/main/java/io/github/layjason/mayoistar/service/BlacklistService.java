package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;

/**
 * 黑名单服务接口。
 *
 * <p>类职责：定义黑名单的拉黑、取消拉黑、查询操作。
 */
public interface BlacklistService {

    /**
     * 将目标用户加入黑名单。
     *
     * <p>前置条件：currentUserId 和 targetUserId 均为有效用户，且不是同一人，且尚未建立黑名单关系。
     *
     * <p>后置条件：创建一条 Blacklist 记录。
     *
     * @param currentUserId 当前用户 ID
     * @param targetUserId  目标用户 ID
     */
    void blockUser(String currentUserId, String targetUserId);

    /**
     * 将目标用户移出黑名单。
     *
     * <p>前置条件：currentUserId 和 targetUserId 之间存在黑名单关系。
     *
     * <p>后置条件：删除对应的 Blacklist 记录。
     *
     * @param currentUserId 当前用户 ID
     * @param targetUserId  目标用户 ID
     */
    void unblockUser(String currentUserId, String targetUserId);

    /**
     * 分页查询当前用户的黑名单列表。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页 BlacklistItem 列表，按创建时间倒序。
     *
     * @param currentUserId 当前用户 ID
     * @param page          页码（从 1 开始）
     * @param pageSize      每页条数
     * @return 黑名单分页结果
     */
    PageResult<SocialDtos.BlacklistItem> listBlacklist(String currentUserId, int page, int pageSize);
}

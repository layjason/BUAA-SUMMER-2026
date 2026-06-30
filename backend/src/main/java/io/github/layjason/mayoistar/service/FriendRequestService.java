package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.entity.social.FriendRequestStatus;

/**
 * 好友申请服务接口。
 *
 * <p>类职责：定义好友申请的发送、处理、查询操作。
 */
public interface FriendRequestService {

    /**
     * 发送好友申请。
     *
     * <p>前置条件：目标用户存在，不是自己，未在黑名单中，不是好友，无待处理申请。
     *
     * <p>后置条件：创建一条 pending 状态的 FriendRequest 记录。
     *
     * @param requesterId 发起者用户 ID
     * @param targetUserId 目标用户 ID
     * @param source       申请来源
     * @param message      申请附言（可选）
     * @return 创建的好友申请 DTO
     */
    SocialDtos.FriendRequest createFriendRequest(
            String requesterId, String targetUserId, FriendRequestSource source, String message);

    /**
     * 处理好友申请（接受或拒绝）。
     *
     * <p>前置条件：requestId 对应的申请为 pending 状态，且当前用户为目标用户。
     *
     * <p>后置条件：状态更新为 accepted 或 rejected。若接受，创建双向好友关系。
     *
     * @param currentUserId 当前用户 ID
     * @param requestId     申请 ID
     * @param accepted      是否接受
     * @return 更新后的好友申请 DTO
     */
    SocialDtos.FriendRequest decideFriendRequest(String currentUserId, String requestId, boolean accepted);

    /**
     * 查询收到的好友申请。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页申请列表，按创建时间倒序。
     *
     * @param userId  当前用户 ID
     * @param status  状态筛选（可选）
     * @param page    页码
     * @param pageSize 每页条数
     * @return 分页申请结果
     */
    PageResult<SocialDtos.FriendRequest> listReceivedRequests(
            String userId, FriendRequestStatus status, int page, int pageSize);

    /**
     * 查询已发送的好友申请。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回分页申请列表，按创建时间倒序。
     *
     * @param userId  当前用户 ID
     * @param status  状态筛选（可选）
     * @param page    页码
     * @param pageSize 每页条数
     * @return 分页申请结果
     */
    PageResult<SocialDtos.FriendRequest> listSentRequests(
            String userId, FriendRequestStatus status, int page, int pageSize);
}

package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import java.util.List;

/**
 * 通知服务接口，负责向受影响的客户端推送实时事件。
 *
 * <p>类职责：将业务事件（消息发送、撤回、转发、好友申请）转换为客户端通知，
 * 解耦业务逻辑与通信层实现。
 *
 * <p>生产环境由 {@link WebSocketNotificationService} 通过 STOMP/WebSocket 推送事件，
 * 测试 profile 由 {@code CapturingNotificationService} 拦截以供断言。
 */
public interface NotificationService {

    /**
     * 通知消息创建事件。
     *
     * <p>前置条件：消息已持久化。
     *
     * <p>后置条件：接收方客户端（排除发送者）能收到 messageCreated 实时事件。
     *
     * @param message          已创建的消息
     * @param recipientUserIds 接收方用户 ID 列表
     */
    void notifyMessageCreated(ChatDtos.ChatMessage message, List<String> recipientUserIds);

    /**
     * 通知消息撤回事件。
     *
     * <p>前置条件：消息 recalled 标记已设置为 true。
     *
     * <p>后置条件：会话中所有成员客户端能收到撤回通知。
     *
     * @param messageId        被撤回消息 ID
     * @param conversationId   所属会话 ID
     * @param recipientUserIds 接收方用户 ID 列表
     */
    void notifyMessageRecalled(String messageId, String conversationId, List<String> recipientUserIds);

    /**
     * 通知消息转发事件。
     *
     * <p>前置条件：转发消息已在新会话中持久化。
     *
     * <p>后置条件：目标会话成员（排除转发者）能收到新消息通知。
     *
     * @param message          转发创建的消息
     * @param recipientUserIds 接收方用户 ID 列表
     */
    void notifyMessageForwarded(ChatDtos.ChatMessage message, List<String> recipientUserIds);

    /**
     * 通知单聊对方已读事件。
     *
     * <p>前置条件：接收方已调用 markMessagesRead 标记消息已读。
     *
     * <p>后置条件：原消息发送方能收到 messagePeerRead 实时事件。
     *
     * @param conversationId 会话标识
     * @param messageId      已被对方阅读的消息标识
     * @param senderUserId   原消息发送方用户 ID
     */
    void notifyMessagePeerRead(String conversationId, String messageId, String senderUserId);

    /**
     * 通知好友申请创建事件。
     *
     * <p>前置条件：好友申请已持久化。
     *
     * <p>后置条件：目标用户能收到新的好友申请通知。
     *
     * @param request 已创建的好友申请
     */
    void notifyFriendRequestCreated(SocialDtos.FriendRequest request);

    /**
     * 通知图片分类任务完成事件（成功或失败）。
     *
     * <p>前置条件：分类任务已在 ClipTaskResultStore 中标记为最终状态，
     * 且分类结果已写入 ai_classification_results 表。
     *
     * <p>后置条件：目标用户通过 /queue/ai-events 收到实时通知。
     *
     * @param event  分类完成事件
     * @param userId 目标用户 ID
     */
    void notifyImageClassificationCompleted(AiDtos.ImageClassificationCompletedEvent event, String userId);
}

package io.github.layjason.mayoistar.exception;

import lombok.experimental.UtilityClass;

/**
 * 业务错误码常量，与 OpenAPI 契约中定义的错误码一一对应。
 */
@UtilityClass
public final class ErrorCodes {

    /* ========== 社交 ========== */

    /** 目标用户不存在或不可见 */
    public static final int USER_NOT_VISIBLE = 40000;

    /** 当前用户与目标用户存在黑名单关系 */
    public static final int BLACKLIST_RELATION_EXISTS = 40001;

    /** 关注关系已存在 */
    public static final int FOLLOW_ALREADY_EXISTS = 40002;

    /** 关注关系不存在 */
    public static final int FOLLOW_NOT_FOUND = 40003;

    /** 好友关系状态不允许当前操作 */
    public static final int FRIENDSHIP_STATE_INVALID = 40004;

    /** 好友申请状态不允许当前操作 */
    public static final int FRIEND_REQUEST_STATE_INVALID = 40005;

    /** 重复好友申请 */
    public static final int DUPLICATE_FRIEND_REQUEST = 40006;

    /** 举报目标或举报内容不合法 */
    public static final int USER_REPORT_INVALID = 40007;

    /* ========== 聊天 ========== */

    /** 会话不存在或调用方不可见 */
    public static final int CONVERSATION_NOT_VISIBLE = 50004;

    /** 调用方不是会话成员 */
    public static final int CONVERSATION_MEMBER_REQUIRED = 50005;

    /** 消息内容与消息类型不匹配 */
    public static final int MESSAGE_CONTENT_INVALID = 50006;

    /** 消息不存在或调用方不可见 */
    public static final int MESSAGE_NOT_VISIBLE = 50007;

    /** 调用方不是消息发送者 */
    public static final int MESSAGE_SENDER_REQUIRED = 50008;

    /** 消息已超过可撤回时间窗口 */
    public static final int MESSAGE_RECALL_EXPIRED = 50009;

    /** 转发目标会话不可用 */
    public static final int FORWARD_TARGET_UNAVAILABLE = 50010;
}

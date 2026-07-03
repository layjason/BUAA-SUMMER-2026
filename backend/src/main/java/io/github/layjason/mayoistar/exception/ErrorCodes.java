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
    public static final int REPORT_INVALID = 40007;

    /** 举报不存在 */
    public static final int REPORT_NOT_FOUND = 60007;

    /** 小队名称已被占用 */
    public static final int TEAM_NAME_UNAVAILABLE = 40008;

    /** 小队不存在、已解散或调用方不可见 */
    public static final int TEAM_NOT_VISIBLE = 40009;

    /** 小队人数已满 */
    public static final int TEAM_FULL = 40010;

    /** 小队已解散或已停用 */
    public static final int TEAM_UNAVAILABLE = 40011;

    /** 用户已经是小队成员 */
    public static final int TEAM_MEMBER_ALREADY_EXISTS = 40012;

    /** 重复入队申请 */
    public static final int DUPLICATE_TEAM_JOIN_REQUEST = 40013;

    /** 入队申请状态不允许当前操作 */
    public static final int TEAM_JOIN_REQUEST_STATE_INVALID = 40014;

    /** 调用方不是小队成员 */
    public static final int TEAM_MEMBER_NOT_FOUND = 40015;

    /** 队长不能直接退出小队 */
    public static final int TEAM_LEADER_CANNOT_LEAVE = 40016;

    /** 小队角色变更不合法 */
    public static final int TEAM_ROLE_CHANGE_INVALID = 40017;

    /** 小队活动不存在或不属于该小队 */
    public static final int TEAM_ACTIVITY_NOT_VISIBLE = 40018;

    /** 黑名单关系不存在 */
    public static final int BLACKLIST_RELATION_NOT_FOUND = 40019;

    /** 调用方缺少小队管理权限 */
    public static final int TEAM_PERMISSION_DENIED = 40020;

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
    /** 调用方无权发布群公告 */
    public static final int ANNOUNCEMENT_PERMISSION_DENIED = 50011;

    /** 群公告不存在或调用方不可见 */
    public static final int ANNOUNCEMENT_NOT_VISIBLE = 50017;

    /** 调用方不是小队成员 */
    public static final int TEAM_MEMBER_REQUIRED = 50002;

    /** 消息引用的媒体文件不存在 */
    public static final int MEDIA_REFERENCE_INVALID = 50018;

    /* ========== AI ========== */

    /** AI 调用频率超过限制 */
    public static final int AI_RATE_LIMITED = 30000;

    /** AI 服务暂不可用 */
    public static final int AI_SERVICE_UNAVAILABLE = 30001;

    /** AI 输出不可用或无法解析 */
    public static final int AI_OUTPUT_UNAVAILABLE = 30002;

    /** 图片媒体文件不存在或不支持 AI 分类 */
    public static final int IMAGE_MEDIA_UNAVAILABLE = 30003;
}

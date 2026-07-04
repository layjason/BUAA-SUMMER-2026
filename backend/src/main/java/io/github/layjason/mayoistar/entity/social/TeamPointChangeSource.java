package io.github.layjason.mayoistar.entity.social;

/**
 * 小队积分变动来源，标识积分增减的触发事件。
 *
 * <p>用于防重和审计追溯，与 referenceId 组合形成唯一约束。
 */
public enum TeamPointChangeSource {
    /** 活动签到加分 */
    checkin,

    /** 爽约扣分（报名但未签到） */
    no_show,

    /** 发布活动图文总结加分 */
    summary_post,

    /** 管理员手动加减分 */
    manual,
}

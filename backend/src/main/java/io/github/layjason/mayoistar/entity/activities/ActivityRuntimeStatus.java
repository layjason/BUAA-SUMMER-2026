package io.github.layjason.mayoistar.entity.activities;

/**
 * 活动运行状态。
 */
public enum ActivityRuntimeStatus {
    notStarted,
    registering,
    registrationClosed,
    ongoing,
    ended,
    takenDown;
}

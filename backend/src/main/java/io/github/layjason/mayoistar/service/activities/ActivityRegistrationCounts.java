package io.github.layjason.mayoistar.service.activities;

/**
 * 活动报名计数快照。
 *
 * <p>registeredCount 统计已确认参加人数（registered + checkedIn）；
 * occupiedCount 统计当前占用名额人数（registered + checkedIn + waitingConfirmation）；
 * waitingCount 统计候补相关人数（waiting + waitingConfirmation）。
 */
public record ActivityRegistrationCounts(int registeredCount, int occupiedCount, int waitingCount) {

    public static ActivityRegistrationCounts zero() {
        return new ActivityRegistrationCounts(0, 0, 0);
    }
}

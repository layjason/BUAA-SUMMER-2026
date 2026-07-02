/**
 * 活动名额占用计算
 *
 * occupiedCount 由 API 返回，表示 registered + checkedIn + waitingConfirmation。
 */

/**
 * 判断活动是否已无可用名额
 *
 * 前置条件：occupiedCount、capacity 为非负整数
 * 后置条件：occupiedCount >= capacity 时返回 true
 *
 * @param occupiedCount API 返回的已占用名额数
 * @param capacity 活动人数上限
 */
export function isActivityAtCapacity(occupiedCount: number, capacity: number): boolean {
  return occupiedCount >= capacity
}

/**
 * 报名与候补 API 模块
 *
 * 封装活动报名、取消报名、候补确认、报名记录查询等接口。
 */
import { get, post } from '@/api/request'
import type { components } from '@/api/types/schema'

type RegisterActivityRequest = components['schemas']['Activities.RegisterActivityRequest']
type WaitingConfirmationRequest = components['schemas']['Activities.WaitingConfirmationRequest']

/** 报名活动 */
export function registerForActivity(activityId: string, options: RegisterActivityRequest) {
  return post('/activities/{activityId}/registrations', {
    path: { activityId },
    body: options,
  })
}

/** 取消报名 */
export function cancelRegistration(activityId: string) {
  return post('/activities/{activityId}/registrations/cancel', {
    path: { activityId },
  })
}

/** 确认候补报名 */
export function confirmWaitlist(activityId: string, confirmed = true) {
  const body: WaitingConfirmationRequest = { confirmed }
  return post('/activities/{activityId}/waiting-confirmations', {
    path: { activityId },
    body,
  })
}

/** 加入候补队列（活动满员时） */
export function joinWaitlist(activityId: string) {
  const body: RegisterActivityRequest = { acceptedSafetyNotice: true }
  return post('/activities/{activityId}/registrations', {
    path: { activityId },
    body,
  })
}

/** 获取我的报名记录列表 */
export function getMyRegistrations(page: number, pageSize: number) {
  return get('/activities/registrations/mine', {
    query: { page, pageSize },
  })
}

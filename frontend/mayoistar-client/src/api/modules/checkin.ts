/**
 * 签到 API 模块
 *
 * 封装签到二维码生成、扫码签到、签到列表查看与导出等接口。
 */
import { get, post } from '@/api/request'
import type { components } from '@/api/types/schema'

type CheckInRequest = components['schemas']['Activities.CheckInRequest']

/** 生成活动签到二维码 */
export function generateCheckInQrCode(activityId: string) {
  return post('/activities/{activityId}/check-in-qrcode', {
    path: { activityId },
  })
}

/**
 * 扫码签到
 *
 * 前置条件：activityId 与 code 非空，currentLocation 在活动要求位置校验时由 uni.getLocation 获取。
 * 后置条件：向 OpenAPI 对齐的签到接口提交二维码 token 与可选当前位置。
 * 不变量：请求体只包含 CheckInRequest 已定义字段。
 */
export function checkIn(
  activityId: string,
  code: string,
  currentLocation?: CheckInRequest['currentLocation'],
) {
  const body: CheckInRequest = currentLocation
    ? { qrCodeToken: code, currentLocation }
    : { qrCodeToken: code }
  return post('/activities/{activityId}/check-ins', {
    path: { activityId },
    body,
  })
}

/** 获取活动签到列表 */
export function getCheckIns(activityId: string, page = 1, pageSize = 100) {
  return get('/activities/{activityId}/check-ins', {
    path: { activityId },
    query: { page, pageSize },
  })
}

/** 导出活动签到数据（返回二进制表格文件） */
export function exportCheckIns(activityId: string): Promise<unknown> {
  return get('/activities/{activityId}/check-ins/export', {
    path: { activityId },
  }) as Promise<unknown>
}

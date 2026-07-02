/**
 * 签到 API 模块
 *
 * 封装签到二维码生成、扫码签到、签到列表查看与导出等接口。
 */
import { get, post } from '@/api/request'

/** 生成活动签到二维码 */
export function generateCheckInQrCode(activityId: string) {
  return post('/activities/{activityId}/check-in-qrcode', {
    path: { activityId },
  })
}

/** 扫码签到 */
export function checkIn(activityId: string, code: string) {
  return post('/activities/{activityId}/check-ins', {
    path: { activityId },
    body: { qrCodeToken: code },
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
export function exportCheckIns(activityId: string) {
  return get('/activities/{activityId}/check-ins/export', {
    path: { activityId },
  })
}

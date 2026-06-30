/**
 * Mock 数据定义
 *
 * 数据模式下所有预定义的 API 响应数据集中存放于此。
 * 修改此文件后重启应用即可生效。
 *
 * key 格式："METHOD /path"，例如 "GET /identity/me/profile"
 * value 为完整的 API 响应 { code, message, data }
 */
import type { MockResponse } from './dev'

export const mockData: Record<string, MockResponse> = {
  /* 示例：编辑资料页个人用户资料
  'GET /identity/me/profile': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: 10001,
      nickname: '测试用户',
      interestTags: ['运动', '桌游'],
      kind: 'personal',
      reputationScore: 100,
    },
  },
  */
}

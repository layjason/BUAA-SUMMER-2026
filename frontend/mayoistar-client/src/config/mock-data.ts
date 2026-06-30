/**
 * Mock 数据定义
 *
 * 数据模式下所有预定义的 API 响应数据集中存放于此。
 * 修改此文件后重启应用即可生效。
 *
 * key 格式："METHOD /path"（不含 query params）
 * value 为完整的 API 响应 { code, message, data }
 */
import type { MockResponse } from './dev'

export const mockData: Record<string, MockResponse> = {
  /* ===== 个人用户资料 ===== */
  'GET /identity/me/profile': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: 10001,
      nickname: '测试用户',
      gender: 'male',
      birthday: '2000-01-15',
      signature: '热爱运动与桌游',
      interestTags: ['运动健身', '桌游聚会'],
      kind: 'personal',
      reputationScore: 100,
    },
  },

  /* ===== 更新个人资料 ===== */
  'PATCH /identity/me/profile': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: 10001,
      nickname: '测试用户',
      gender: 'male',
      birthday: '2000-01-15',
      signature: '热爱运动与桌游',
      interestTags: ['运动健身', '桌游聚会'],
      kind: 'personal',
      reputationScore: 100,
    },
  },

  /* ===== 商家用户资料 ===== */
  'GET /identity/me/merchant-profile': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: 10001,
      merchantNickname: '测试商家',
      merchantName: '测试餐饮有限公司',
      interestedActivityFields: ['美食餐饮', '城市探索'],
      accountStatus: 'active',
      qualificationStatus: 'approved',
      qualification: {
        status: 'approved',
        submittedAt: '2026-01-10T08:00:00Z',
        reviewedAt: '2026-01-12T10:30:00Z',
      },
    },
  },

  /* ===== 更新商家资料 ===== */
  'PATCH /identity/me/merchant-profile': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: 10001,
      merchantNickname: '测试商家',
      merchantName: '测试餐饮有限公司',
      interestedActivityFields: ['美食餐饮', '城市探索'],
      accountStatus: 'active',
      qualificationStatus: 'approved',
      qualification: {
        status: 'approved',
        submittedAt: '2026-01-10T08:00:00Z',
        reviewedAt: '2026-01-12T10:30:00Z',
      },
    },
  },

  /* ===== 兴趣标签列表 ===== */
  'GET /identity/interest-tags': {
    code: 200,
    message: 'For Super Earth!',
    data: [
      { name: '运动健身' },
      { name: '户外徒步' },
      { name: '桌游聚会' },
      { name: '学习交流' },
      { name: '公益活动' },
      { name: '城市探索' },
      { name: '美食餐饮' },
      { name: '摄影采风' },
    ],
  },

  /* ===== 昵称可用性校验（任意昵称均返回可用） ===== */
  'GET /identity/nicknames/availability': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      nickname: '',
      available: true,
    },
  },
}

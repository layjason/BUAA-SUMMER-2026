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
  /* ========================= 身份与资料 ========================= */

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

  'PUT /identity/me/profile': {
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

  'PUT /identity/me/merchant-profile': {
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

  'GET /identity/nicknames/availability': {
    code: 200,
    message: 'For Super Earth!',
    data: { nickname: '', available: true },
  },

  /* 商家资质提交 */
  'POST /identity/me/merchant-qualification': {
    code: 200,
    message: 'For Super Earth!',
    data: {},
  },

  /* 头像上传 */
  'POST /identity/media/avatar': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      mediaId: 9001,
      url: 'https://picsum.photos/200',
    },
  },

  /* ========================= 活动 ========================= */

  'GET /activities/mine': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          activityId: 2001,
          title: '周末羽毛球友谊赛',
          capacity: 16,
          startAt: '2026-07-05T14:00:00Z',
          endAt: '2026-07-05T17:00:00Z',
          location: {
            longitude: 116.347,
            latitude: 39.972,
            city: '北京',
            address: '海淀区体育馆羽毛球馆',
            name: '海淀体育馆',
          },
          registeredCount: 12,
          tags: ['运动健身'],
          reviewStatus: 'approved',
          runtimeStatus: 'open',
          feeAmount: 30.0,
        },
        {
          activityId: 2002,
          title: '桌游之夜——三国杀专场',
          capacity: 8,
          startAt: '2026-07-12T19:00:00Z',
          endAt: '2026-07-12T22:00:00Z',
          location: {
            longitude: 116.337,
            latitude: 39.982,
            city: '北京',
            address: '五道口桌游吧',
            name: '五道口桌游吧',
          },
          registeredCount: 8,
          tags: ['桌游聚会'],
          reviewStatus: 'approved',
          runtimeStatus: 'full',
          feeAmount: 50.0,
        },
      ],
      total: 2,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  'GET /activities/drafts': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          activityId: 3001,
          title: '（草稿）周末徒步香山',
          createdAt: '2026-06-28T10:00:00Z',
          updatedAt: '2026-06-30T22:00:00Z',
          reviewStatus: 'draft',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /* ========================= 活动报名 ========================= */

  'GET /activities/my-registrations': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          registrationId: 4001,
          activityId: 2010,
          activityTitle: '城市徒步——探寻胡同文化',
          status: 'registered',
          registeredAt: '2026-06-25T09:00:00Z',
          activityStartAt: '2026-07-08T08:30:00Z',
          coverImage: null,
        },
        {
          registrationId: 4002,
          activityId: 2015,
          activityTitle: '公益环保志愿行',
          status: 'checkedIn',
          registeredAt: '2026-06-20T12:00:00Z',
          activityStartAt: '2026-06-22T07:00:00Z',
          coverImage: null,
        },
        {
          registrationId: 4003,
          activityId: 2020,
          activityTitle: '摄影采风——颐和园荷花季',
          status: 'canceled',
          registeredAt: '2026-06-15T14:00:00Z',
          activityStartAt: '2026-07-15T06:00:00Z',
          coverImage: null,
        },
      ],
      total: 3,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /* ========================= 好友 ========================= */

  'GET /social/friends': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          userId: 10002,
          nickname: '小明',
          remark: '高中同学',
          groupTags: ['同学', '运动搭子'],
          source: 'team',
        },
        {
          userId: 10003,
          nickname: '小红',
          remark: '',
          groupTags: [],
          source: 'activityParticipants',
        },
      ],
      total: 2,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  'GET /social/friend-requests/received': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          requestId: 5001,
          requesterId: 10004,
          requesterNickname: '新朋友A',
          message: '你好，上次活动一起参加过',
          source: 'activityParticipants',
          status: 'pending',
          createdAt: '2026-07-01T10:00:00Z',
        },
        {
          requestId: 5002,
          requesterId: 10005,
          requesterNickname: '新朋友B',
          message: '',
          source: 'team',
          status: 'pending',
          createdAt: '2026-06-30T15:00:00Z',
        },
      ],
      total: 2,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  'GET /social/friend-requests/sent': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          requestId: 5003,
          targetUserId: 10006,
          targetNickname: '想认识的人',
          message: '通过小队认识的，加个好友吧',
          source: 'team',
          status: 'pending',
          createdAt: '2026-06-29T08:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /* ========================= 黑名单 ========================= */

  'GET /social/blacklist': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          userId: 10099,
          nickname: '骚扰用户',
          blockedAt: '2026-06-15T12:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /* ========================= 小队 ========================= */

  'GET /social/teams': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          teamId: 6001,
          name: '海淀桌游爱好者',
          description: '每周五组织线下桌游活动，欢迎加入！',
          tags: ['桌游聚会'],
          capacity: 50,
          memberCount: 23,
          leaderId: 10001,
          chatId: 7001,
          joinMode: 'public',
          status: 'active',
        },
        {
          teamId: 6002,
          name: '户外徒步小分队',
          description: '周末京郊徒步，不定期组织',
          tags: ['户外徒步'],
          capacity: 30,
          memberCount: 18,
          leaderId: 10002,
          chatId: 7002,
          joinMode: 'review',
          status: 'active',
        },
      ],
      total: 2,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /* ========================= 社交资料 ========================= */

  'GET /social/profiles/10002': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: 10002,
      nickname: '小明',
      signature: '爱运动爱生活',
      interestTags: ['运动健身', '户外徒步'],
      gender: 'male',
      birthday: '1999-05-20',
      kind: 'personal',
      reputationScore: 95,
    },
  },
}

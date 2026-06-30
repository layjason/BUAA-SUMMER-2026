/**
 * Mock 数据定义
 *
 * 数据模式下所有预定义的 API 响应数据集中存放于此。
 * 修改此文件后重启应用即可生效。
 *
 * 每条数据尽可能覆盖该模型的不同状态/类型组合。
 *
 * key 格式："METHOD /path"（不含 query params，method 为实际 HTTP 方法）
 * value 为完整的 API 响应 { code=200, message, data }
 */
import type { MockResponse } from './dev'

export const mockData: Record<string, MockResponse> = {
  /* ===================== 身份与资料 ===================== */

  'GET /identity/me/profile': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: '10001',
      nickname: '测试用户',
      avatar: null,
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
      userId: '10001',
      nickname: '测试用户',
      avatar: null,
      gender: 'male',
      birthday: '2000-01-15',
      signature: '热爱运动与桌游',
      interestTags: ['运动健身', '桌游聚会'],
      kind: 'personal',
      reputationScore: 100,
    },
  },

  /* 商家：已通过审核 + 资质完整 */
  'GET /identity/me/merchant-profile': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: '10001',
      avatar: null,
      merchantNickname: '趣聚咖啡',
      merchantName: '趣聚咖啡（北京）有限公司',
      interestedActivityFields: ['美食餐饮', '城市探索'],
      accountStatus: 'active',
      qualificationStatus: 'approved',
      qualification: {
        status: 'approved',
        submittedAt: '2026-01-10T08:00:00Z',
        reviewedAt: '2026-01-12T10:30:00Z',
        rejectReason: null,
        licenseImageUrls: ['https://picsum.photos/400/300?random=1'],
      },
    },
  },

  'PUT /identity/me/merchant-profile': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: '10001',
      avatar: null,
      merchantNickname: '趣聚咖啡',
      merchantName: '趣聚咖啡（北京）有限公司',
      interestedActivityFields: ['美食餐饮', '城市探索'],
      accountStatus: 'active',
      qualificationStatus: 'approved',
      qualification: {
        status: 'approved',
        submittedAt: '2026-01-10T08:00:00Z',
        reviewedAt: '2026-01-12T10:30:00Z',
        rejectReason: null,
        licenseImageUrls: ['https://picsum.photos/400/300?random=1'],
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

  'POST /identity/me/merchant-qualification': {
    code: 200,
    message: 'For Super Earth!',
    data: {},
  },

  'POST /identity/media/avatar': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      mediaId: '9001',
      url: 'https://picsum.photos/200',
      contentType: 'image/jpeg',
      fileName: 'avatar.jpg',
      sizeBytes: 102400,
      uploadedAt: '2026-07-01T12:00:00Z',
      usage: 'avatar',
    },
  },

  /* ===================== 活动 ===================== */

  /*
   * runtimeStatus 全覆盖: registering | registrationClosed | ongoing | ended | notStarted | takenDown
   * reviewStatus: approved | pending | rejected
   * feeAmount: 0.0 (免费) | 正整数 (付费)
   * coverImage: null | 有图片
   * registeredCount: 0 | 部分 | 满员
   */
  'GET /activities/mine': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          activityId: '2001',
          title: '周末羽毛球友谊赛',
          capacity: 16,
          startAt: '2026-07-05T14:00:00Z',
          endAt: '2026-07-05T17:00:00Z',
          location: {
            address: '海淀区体育馆羽毛球馆',
            city: '北京',
            placeName: '海淀体育馆',
            point: { longitude: 116.347, latitude: 39.972 },
          },
          registeredCount: 12,
          tags: ['运动健身'],
          reviewStatus: 'approved',
          runtimeStatus: 'registering',
          coverImage: null,
          feeAmount: 30.0,
        },
        {
          activityId: '2002',
          title: '桌游之夜——三国杀专场',
          capacity: 8,
          startAt: '2026-07-12T19:00:00Z',
          endAt: '2026-07-12T22:00:00Z',
          location: {
            address: '五道口桌游吧',
            city: '北京',
            placeName: '五道口桌游吧',
            point: { longitude: 116.337, latitude: 39.982 },
          },
          registeredCount: 8,
          tags: ['桌游聚会'],
          reviewStatus: 'approved',
          runtimeStatus: 'registrationClosed',
          coverImage: { url: 'https://picsum.photos/400/200?random=2', mediaId: 'img2002' },
          feeAmount: 50.0,
        },
        {
          activityId: '2003',
          title: '公益植树活动',
          capacity: 30,
          startAt: '2026-07-20T08:00:00Z',
          endAt: '2026-07-20T12:00:00Z',
          location: {
            address: '朝阳公园',
            city: '北京',
            placeName: '朝阳公园南门',
            point: { longitude: 116.471, latitude: 39.942 },
          },
          registeredCount: 25,
          tags: ['公益活动'],
          reviewStatus: 'approved',
          runtimeStatus: 'registering',
          coverImage: null,
          feeAmount: 0.0,
        },
        {
          activityId: '2004',
          title: '英语角——职场口语练习',
          capacity: 20,
          startAt: '2026-07-25T19:00:00Z',
          endAt: '2026-07-25T21:00:00Z',
          location: {
            address: '朝阳区三里屯 soho B 座 3 层',
            city: '北京',
            placeName: '三里屯共享会议室',
            point: { longitude: 116.454, latitude: 39.931 },
          },
          registeredCount: 0,
          tags: ['学习交流'],
          reviewStatus: 'pending',
          runtimeStatus: 'notStarted',
          coverImage: null,
          feeAmount: 0.0,
        },
        {
          activityId: '2005',
          title: '胡同骑行探索',
          capacity: 10,
          startAt: '2026-07-10T08:00:00Z',
          endAt: '2026-07-10T16:00:00Z',
          location: {
            address: '东城区南锣鼓巷地铁站 E 口',
            city: '北京',
            placeName: '南锣鼓巷地铁站',
            point: { longitude: 116.403, latitude: 39.937 },
          },
          registeredCount: 10,
          tags: ['户外徒步', '运动健身'],
          reviewStatus: 'approved',
          runtimeStatus: 'ongoing',
          coverImage: { url: 'https://picsum.photos/400/200?random=5', mediaId: 'img2005' },
          feeAmount: 20.0,
        },
        {
          activityId: '2006',
          title: '中秋赏月诗词会',
          capacity: 25,
          startAt: '2026-06-15T19:00:00Z',
          endAt: '2026-06-15T22:00:00Z',
          location: {
            address: '上海浦东海滨公园',
            city: '上海',
            placeName: '滨海公园',
            point: { longitude: 121.606, latitude: 31.187 },
          },
          registeredCount: 25,
          tags: ['学习交流', '公益活动'],
          reviewStatus: 'approved',
          runtimeStatus: 'ended',
          coverImage: { url: 'https://picsum.photos/400/200?random=6', mediaId: 'img2006' },
          feeAmount: 0.0,
        },
        {
          activityId: '2007',
          title: '违规活动（示例）',
          capacity: 50,
          startAt: '2026-08-01T10:00:00Z',
          endAt: '2026-08-01T18:00:00Z',
          location: {
            address: '示例地址',
            city: '北京',
            placeName: null,
            point: { longitude: 116.4, latitude: 39.9 },
          },
          registeredCount: 0,
          tags: [],
          reviewStatus: 'rejected',
          runtimeStatus: 'takenDown',
          coverImage: null,
          feeAmount: 0.0,
        },
      ],
      total: 7,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /*
   * 草稿 reviewStatus: draft | rejected
   */
  'GET /activities/drafts': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          activityId: '3001',
          title: '（草稿）周末徒步香山',
          createdAt: '2026-06-28T10:00:00Z',
          updatedAt: '2026-06-30T22:00:00Z',
          reviewStatus: 'draft',
        },
        {
          activityId: '3002',
          title: '（草稿）摄影采风——颐和园',
          createdAt: '2026-07-01T09:00:00Z',
          updatedAt: '2026-07-01T09:00:00Z',
          reviewStatus: 'draft',
        },
        {
          activityId: '3003',
          title: '（已驳回）夜间登山活动',
          createdAt: '2026-06-20T15:00:00Z',
          updatedAt: '2026-06-21T10:00:00Z',
          reviewStatus: 'rejected',
        },
      ],
      total: 3,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /* ===================== 活动报名 ===================== */

  /*
   * status 全覆盖: registered | waiting | waitingConfirmation | checkedIn | canceled
   */
  'GET /activities/my-registrations': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          registrationId: '4001',
          activityId: '2010',
          activityTitle: '城市徒步——探寻胡同文化',
          status: 'registered',
          registeredAt: '2026-06-25T09:00:00Z',
          activityStartAt: '2026-07-08T08:30:00Z',
        },
        {
          registrationId: '4002',
          activityId: '2015',
          activityTitle: '公益环保志愿行',
          status: 'checkedIn',
          registeredAt: '2026-06-20T12:00:00Z',
          activityStartAt: '2026-06-22T07:00:00Z',
        },
        {
          registrationId: '4003',
          activityId: '2020',
          activityTitle: '摄影采风——颐和园荷花季',
          status: 'canceled',
          registeredAt: '2026-06-15T14:00:00Z',
          activityStartAt: '2026-07-15T06:00:00Z',
        },
        {
          registrationId: '4004',
          activityId: '2025',
          activityTitle: '周末露营——海坨山谷',
          status: 'waiting',
          registeredAt: '2026-07-01T10:00:00Z',
          activityStartAt: '2026-07-22T07:00:00Z',
        },
        {
          registrationId: '4005',
          activityId: '2030',
          activityTitle: '室内攀岩体验日',
          status: 'waitingConfirmation',
          registeredAt: '2026-07-02T08:00:00Z',
          activityStartAt: '2026-07-18T14:00:00Z',
        },
      ],
      total: 5,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /* ===================== 好友 ===================== */

  /*
   * source: manualRequest | mutualFollow
   * remark: 有 | 空
   * groupTags: 有 | 空 | 多标签
   */
  'GET /social/friends': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          userId: '10002',
          nickname: '小明',
          avatar: null,
          remark: '高中同学',
          groupTags: ['同学', '运动搭子'],
          source: 'mutualFollow',
        },
        {
          userId: '10003',
          nickname: '小红',
          avatar: { url: 'https://picsum.photos/100?random=3', mediaId: 'img1002' },
          remark: '',
          groupTags: [],
          source: 'manualRequest',
        },
        {
          userId: '10004',
          nickname: '登山达人老李',
          avatar: null,
          remark: '户外领队',
          groupTags: ['户外', '领队', '周末搭子'],
          source: 'mutualFollow',
        },
      ],
      total: 3,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /*
   * source 全覆盖: profile | activityParticipants | team | qrCode
   * status: pending | accepted | rejected
   */
  'GET /social/friend-requests/received': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          requestId: '5001',
          requesterId: '10010',
          targetUserId: '10001',
          message: '你好，上次活动一起参加过',
          source: 'activityParticipants',
          status: 'pending',
          createdAt: '2026-07-01T10:00:00Z',
        },
        {
          requestId: '5002',
          requesterId: '10011',
          targetUserId: '10001',
          message: '',
          source: 'team',
          status: 'pending',
          createdAt: '2026-06-30T15:00:00Z',
        },
        {
          requestId: '5003',
          requesterId: '10012',
          targetUserId: '10001',
          message: '看了你的主页，想认识一下',
          source: 'profile',
          status: 'pending',
          createdAt: '2026-07-02T09:00:00Z',
        },
        {
          requestId: '5004',
          requesterId: '10013',
          targetUserId: '10001',
          message: '',
          source: 'qrCode',
          status: 'accepted',
          createdAt: '2026-06-25T12:00:00Z',
        },
        {
          requestId: '5005',
          requesterId: '10014',
          targetUserId: '10001',
          message: '加个好友吧',
          source: 'profile',
          status: 'rejected',
          createdAt: '2026-06-20T08:00:00Z',
        },
      ],
      total: 5,
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
          requestId: '5101',
          requesterId: '10001',
          targetUserId: '10020',
          message: '通过小队认识的，加个好友吧',
          source: 'team',
          status: 'pending',
          createdAt: '2026-06-29T08:00:00Z',
        },
        {
          requestId: '5102',
          requesterId: '10001',
          targetUserId: '10021',
          message: '',
          source: 'activityParticipants',
          status: 'accepted',
          createdAt: '2026-06-22T14:00:00Z',
        },
        {
          requestId: '5103',
          requesterId: '10001',
          targetUserId: '10022',
          message: '你好',
          source: 'qrCode',
          status: 'rejected',
          createdAt: '2026-06-15T10:00:00Z',
        },
      ],
      total: 3,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /*
   * 黑名单：有 | 无 头像
   */
  'GET /social/blacklist': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          userId: '10099',
          nickname: '骚扰用户',
          avatar: null,
          blockedAt: '2026-06-15T12:00:00Z',
        },
        {
          userId: '10100',
          nickname: '广告推广号',
          avatar: { url: 'https://picsum.photos/100?random=9', mediaId: 'img9901' },
          blockedAt: '2026-07-01T09:00:00Z',
        },
      ],
      total: 2,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /* ===================== 小队 ===================== */

  /*
   * joinMode: publicJoin | approvalRequired
   * status: active | dissolved
   * memberCount: 有 | 0
   * description: 有 | 空
   * avatar: 有 | 无
   */
  'GET /social/teams': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      items: [
        {
          teamId: '6001',
          name: '海淀桌游爱好者',
          description: '每周五组织线下桌游活动，欢迎加入！',
          tags: ['桌游聚会'],
          capacity: 50,
          memberCount: 23,
          leaderId: '10001',
          chatId: '7001',
          joinMode: 'publicJoin',
          status: 'active',
          avatar: { url: 'https://picsum.photos/100?random=6', mediaId: 'img6001' },
        },
        {
          teamId: '6002',
          name: '户外徒步小分队',
          description: '周末京郊徒步，不定期组织',
          tags: ['户外徒步'],
          capacity: 30,
          memberCount: 18,
          leaderId: '10002',
          chatId: '7002',
          joinMode: 'approvalRequired',
          status: 'active',
          avatar: null,
        },
        {
          teamId: '6003',
          name: '新成立的摄影小组',
          description: '',
          tags: ['摄影采风'],
          capacity: 20,
          memberCount: 0,
          leaderId: '10003',
          chatId: '7003',
          joinMode: 'publicJoin',
          status: 'active',
          avatar: null,
        },
        {
          teamId: '6004',
          name: '已解散的读书会',
          description: '阅读分享与交流',
          tags: ['学习交流'],
          capacity: 15,
          memberCount: 5,
          leaderId: '10001',
          chatId: '7004',
          joinMode: 'approvalRequired',
          status: 'dissolved',
          avatar: null,
        },
      ],
      total: 4,
      page: 1,
      pageSize: 20,
      totalPages: 1,
    },
  },

  /* ===================== 社交资料 ===================== */

  /* 资料完整 */
  'GET /social/profiles/10002': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: '10002',
      nickname: '小明',
      avatar: { url: 'https://picsum.photos/100?random=3', mediaId: 'img1002' },
      signature: '爱运动爱生活',
      interestTags: ['运动健身', '户外徒步'],
      gender: 'male',
      birthday: '1999-05-20',
      kind: 'personal',
      reputationScore: 95,
    },
  },

  /* 资料不完整（无签名、无性别、无生日） */
  'GET /social/profiles/10010': {
    code: 200,
    message: 'For Super Earth!',
    data: {
      userId: '10010',
      nickname: '新用户001',
      avatar: null,
      signature: null,
      interestTags: [],
      gender: null,
      birthday: null,
      kind: 'personal',
      reputationScore: 50,
    },
  },
}

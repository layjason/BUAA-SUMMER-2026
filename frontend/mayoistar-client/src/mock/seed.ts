/**
 * Mock 种子数据工厂
 *
 * 生成包含完整初始状态的 MockDatabase 实例。
 * 所有时间使用 ISO 8601 格式，活动分布在不同的运行状态中。
 */
import type {
  MockUser,
  MockActivity,
  MockDraft,
  MockRegistration,
  MockWaitlistEntry,
  MockCheckIn,
  MockReview,
  MockSummary,
  MockFriend,
  MockFriendRequest,
  MockFollow,
  MockBlacklist,
  MockConversation,
  MockMessage,
  MockTeam,
  MockTeamMember,
  MockTeamJoinRequest,
  MockInterestTag,
  MockTemplate,
} from './types'
import type { MockDatabase } from './database'

/** 生成 ISO 时间字符串 */
function iso(daysFromNow: number, hours = 10, minutes = 0): string {
  const d = new Date()
  d.setDate(d.getDate() + daysFromNow)
  d.setHours(hours, minutes, 0, 0)
  return d.toISOString()
}

/** 生成用户头像占位图 URL */
function avatar(id: number): string {
  return `https://picsum.photos/seed/avatar${id}/128/128`
}

/** 生成活动封面占位图 URL */
function cover(id: number): string {
  return `https://picsum.photos/seed/activity${id}/400/225`
}

/**
 * 创建种子数据
 *
 * 前置条件：无
 * 后置条件：返回完整的 MockDatabase，registeredCount 与 registrations 记录一致
 */
export function createSeedData(): MockDatabase {
  /* ---- 用户 ---- */
  const users: MockUser[] = [
    {
      id: 10001,
      email: 'user@example.com',
      password: 'Pass1234',
      nickname: '小明同学',
      avatarUrl: avatar(10001),
      kind: 'personal',
      accountStatus: 'active',
      gender: 'male',
      birthday: '2000-05-15',
      signature: '生活不止眼前的代码，还有诗和远方',
      interestTagIds: [1, 3, 5],
      createdAt: iso(-180),
    },
    {
      id: 10002,
      email: 'merchant@example.com',
      password: 'Pass1234',
      nickname: '趣动工作室',
      avatarUrl: avatar(10002),
      kind: 'merchant',
      accountStatus: 'active',
      gender: 'unknown',
      birthday: '',
      signature: '专业活动策划，让每次相聚都有意义',
      interestTagIds: [1, 2, 4],
      createdAt: iso(-365),
    },
    {
      id: 10003,
      email: 'organizer@example.com',
      password: 'Pass1234',
      nickname: '户外达人阿杰',
      avatarUrl: avatar(10003),
      kind: 'personal',
      accountStatus: 'active',
      gender: 'male',
      birthday: '1995-08-22',
      signature: '走遍北京每一条徒步路线',
      interestTagIds: [3, 6, 7],
      createdAt: iso(-200),
    },
    {
      id: 10004,
      email: 'banned@example.com',
      password: 'Pass1234',
      nickname: '违规用户',
      avatarUrl: avatar(10004),
      kind: 'personal',
      accountStatus: 'banned',
      gender: 'unknown',
      birthday: '',
      signature: '',
      interestTagIds: [],
      createdAt: iso(-100),
    },
    {
      id: 10005,
      email: 'inactive@example.com',
      password: 'Pass1234',
      nickname: '未激活用户',
      avatarUrl: avatar(10005),
      kind: 'personal',
      accountStatus: 'inactive',
      gender: 'unknown',
      birthday: '',
      signature: '',
      interestTagIds: [],
      createdAt: iso(-10),
    },
    {
      id: 10006,
      email: 'xiaohong@example.com',
      password: 'Pass1234',
      nickname: '小红',
      avatarUrl: avatar(10006),
      kind: 'personal',
      accountStatus: 'active',
      gender: 'female',
      birthday: '2001-03-10',
      signature: '爱摄影、爱旅行',
      interestTagIds: [2, 5],
      createdAt: iso(-150),
    },
    {
      id: 10007,
      email: 'xiaolan@example.com',
      password: 'Pass1234',
      nickname: '小蓝',
      avatarUrl: avatar(10007),
      kind: 'personal',
      accountStatus: 'active',
      gender: 'male',
      birthday: '1999-11-28',
      signature: '桌游爱好者',
      interestTagIds: [4, 8],
      createdAt: iso(-120),
    },
    {
      id: 10008,
      email: 'xiaolv@example.com',
      password: 'Pass1234',
      nickname: '小绿',
      avatarUrl: avatar(10008),
      kind: 'personal',
      accountStatus: 'active',
      gender: 'female',
      birthday: '2002-07-04',
      signature: '环保志愿者',
      interestTagIds: [3, 9],
      createdAt: iso(-90),
    },
    {
      id: 10009,
      email: 'xiaohuang@example.com',
      password: 'Pass1234',
      nickname: '小黄',
      avatarUrl: avatar(10009),
      kind: 'personal',
      accountStatus: 'active',
      gender: 'male',
      birthday: '1998-01-20',
      signature: '跑步打卡第 365 天',
      interestTagIds: [1, 6],
      createdAt: iso(-80),
    },
    {
      id: 10010,
      email: 'xiaozi@example.com',
      password: 'Pass1234',
      nickname: '小紫',
      avatarUrl: avatar(10010),
      kind: 'personal',
      accountStatus: 'active',
      gender: 'female',
      birthday: '2000-12-01',
      signature: '读书是最好的投资',
      interestTagIds: [5, 10],
      createdAt: iso(-60),
    },
  ]

  /* ---- 兴趣标签 ---- */
  const interestTags: MockInterestTag[] = [
    { id: 1, name: '羽毛球', category: '运动' },
    { id: 2, name: '摄影', category: '文化' },
    { id: 3, name: '徒步', category: '户外' },
    { id: 4, name: '桌游', category: '社交' },
    { id: 5, name: '读书', category: '学习' },
    { id: 6, name: '跑步', category: '运动' },
    { id: 7, name: '骑行', category: '户外' },
    { id: 8, name: '剧本杀', category: '社交' },
    { id: 9, name: '环保', category: '学习' },
    { id: 10, name: '编程', category: '学习' },
    { id: 11, name: '瑜伽', category: '运动' },
    { id: 12, name: '绘画', category: '文化' },
  ]

  /* ---- 活动 ---- */

  // 报名人数计数（稍后用于填充 registeredCount）
  const regCountMap: Record<number, number> = {}
  function incReg(activityId: number): void {
    regCountMap[activityId] = (regCountMap[activityId] ?? 0) + 1
  }

  const activities: MockActivity[] = [
    // 1: 报名中 - 羽毛球
    {
      id: 1,
      creatorId: 10003,
      title: '周末羽毛球友谊赛',
      introduction: '欢迎各路羽毛球高手来切磋！场地已预定两块场地，自带球拍，球由组织者提供。',
      safetyNotice: '请穿着运动鞋，活动前做好热身，注意防护。',
      coverUrl: cover(1),
      images: [cover(1)],
      startTime: iso(3, 14, 0),
      endTime: iso(3, 17, 0),
      registrationDeadline: iso(2, 23, 59),
      location: {
        longitude: 116.397,
        latitude: 39.908,
        city: '北京',
        address: '朝阳区奥林匹克体育中心羽毛球馆',
        placeName: '奥林匹克体育中心',
      },
      fee: 30,
      capacity: 3,
      registeredCount: 0, // 后面填充
      minAge: 16,
      tags: ['羽毛球', '运动', '周末'],
      runtimeStatus: 'registering',
      reviewStatus: 'approved',
      isTakenDown: false,
      createdAt: iso(-5),
    },
    // 2: 报名中 - 骑行
    {
      id: 2,
      creatorId: 10003,
      title: '城市骑行探索之旅',
      introduction: '从国贸出发，沿长安街骑行至颐和园，全程约 25 公里，沿途欣赏北京城市风光。',
      safetyNotice: '必须佩戴头盔，遵守交通规则，夜间骑行请配备车灯。',
      coverUrl: cover(2),
      images: [cover(2)],
      startTime: iso(7, 8, 0),
      endTime: iso(7, 16, 0),
      registrationDeadline: iso(5, 23, 59),
      location: {
        longitude: 116.46,
        latitude: 39.908,
        city: '北京',
        address: '朝阳区国贸地铁站 A 口',
        placeName: '国贸地铁站',
      },
      fee: 0,
      capacity: 30,
      registeredCount: 0,
      minAge: 18,
      tags: ['骑行', '户外', '城市探索'],
      runtimeStatus: 'registering',
      reviewStatus: 'approved',
      isTakenDown: false,
      createdAt: iso(-3),
    },
    // 3: 报名中 - 桌游
    {
      id: 3,
      creatorId: 10007,
      title: '桌游之夜：卡坦岛争霸赛',
      introduction: '经典桌游卡坦岛主题夜，分组对抗，最终决出冠军！提供茶歇小食。',
      safetyNotice: '请准时到场，迟到 15 分钟视为弃权。',
      coverUrl: cover(3),
      images: [cover(3)],
      startTime: iso(5, 19, 0),
      endTime: iso(5, 22, 0),
      registrationDeadline: iso(4, 20, 0),
      location: {
        longitude: 116.353,
        latitude: 39.938,
        city: '北京',
        address: '海淀区中关村创业大街 3W 咖啡',
        placeName: '3W 咖啡',
      },
      fee: 50,
      capacity: 12,
      registeredCount: 0,
      minAge: 14,
      tags: ['桌游', '社交', '卡坦岛'],
      runtimeStatus: 'registering',
      reviewStatus: 'approved',
      isTakenDown: false,
      createdAt: iso(-2),
    },
    // 4: 报名中 - 读书会
    {
      id: 4,
      creatorId: 10010,
      title: '读书会：科幻小说分享',
      introduction: '本期主题：黄金时代科幻。每人准备一本你最爱的科幻小说，分享阅读心得。',
      safetyNotice: '请尊重他人观点，文明讨论。',
      coverUrl: cover(4),
      images: [cover(4)],
      startTime: iso(4, 14, 0),
      endTime: iso(4, 17, 0),
      registrationDeadline: iso(3, 23, 59),
      location: {
        longitude: 116.417,
        latitude: 39.929,
        city: '北京',
        address: '东城区王府井书店三层活动区',
        placeName: '王府井书店',
      },
      fee: 0,
      capacity: 20,
      registeredCount: 0,
      minAge: 12,
      tags: ['读书', '科幻', '学习'],
      runtimeStatus: 'registering',
      reviewStatus: 'approved',
      isTakenDown: false,
      createdAt: iso(-4),
    },
    // 5: 报名中（满员） - 候补演示
    {
      id: 5,
      creatorId: 10002,
      title: '公益环保徒步：百望山净山行动',
      introduction: '边徒步边捡拾垃圾，为环保出一份力！路线约 8 公里，难度低，适合所有人。',
      safetyNotice: '请携带手套和垃圾袋，组织方也会提供部分物资。',
      coverUrl: cover(5),
      images: [cover(5)],
      startTime: iso(3, 9, 0),
      endTime: iso(3, 13, 0),
      registrationDeadline: iso(2, 23, 59),
      location: {
        longitude: 116.272,
        latitude: 40.036,
        city: '北京',
        address: '海淀区百望山森林公园东门',
        placeName: '百望山森林公园',
      },
      fee: 0,
      capacity: 7,
      registeredCount: 0,
      minAge: 8,
      tags: ['环保', '徒步', '公益'],
      runtimeStatus: 'registering',
      reviewStatus: 'approved',
      isTakenDown: false,
      createdAt: iso(-15),
    },
    // 6: 进行中
    {
      id: 6,
      creatorId: 10003,
      title: '夜跑团：奥森公园 5 公里',
      introduction: '每周三晚奥森夜跑，5 公里轻松跑，配速不限，一起坚持！',
      safetyNotice: '夜间跑步请注意安全，穿着反光装备。',
      coverUrl: cover(6),
      images: [cover(6)],
      startTime: iso(0, 19, 30),
      endTime: iso(0, 21, 0),
      registrationDeadline: iso(0, 18, 0),
      location: {
        longitude: 116.397,
        latitude: 40.02,
        city: '北京',
        address: '朝阳区奥林匹克森林公园南门',
        placeName: '奥林匹克森林公园',
      },
      fee: 0,
      capacity: 50,
      registeredCount: 0,
      minAge: 16,
      tags: ['跑步', '运动', '夜跑'],
      runtimeStatus: 'ongoing',
      reviewStatus: 'approved',
      isTakenDown: false,
      createdAt: iso(-7),
    },
    // 7: 已结束
    {
      id: 7,
      creatorId: 10001,
      title: '周末摄影外拍：故宫角楼日落',
      introduction: '在故宫角楼拍摄经典日落场景，互相学习摄影技巧。',
      safetyNotice: '请保管好个人器材，注意防盗。',
      coverUrl: cover(7),
      images: [cover(7)],
      startTime: iso(-3, 16, 0),
      endTime: iso(-3, 19, 0),
      registrationDeadline: iso(-5, 23, 59),
      location: {
        longitude: 116.391,
        latitude: 39.923,
        city: '北京',
        address: '东城区故宫角楼',
        placeName: '故宫角楼',
      },
      fee: 0,
      capacity: 15,
      registeredCount: 0,
      minAge: 12,
      tags: ['摄影', '文化', '故宫'],
      runtimeStatus: 'ended',
      reviewStatus: 'approved',
      isTakenDown: false,
      createdAt: iso(-20),
    },
    // 8: 已下架
    {
      id: 8,
      creatorId: 10004,
      title: '违规活动示例',
      introduction: '该活动因违规已被下架。',
      safetyNotice: '',
      coverUrl: cover(8),
      images: [],
      startTime: iso(10, 10, 0),
      endTime: iso(10, 18, 0),
      registrationDeadline: iso(8, 23, 59),
      location: {
        longitude: 116.4,
        latitude: 39.9,
        city: '北京',
        address: '某地点',
        placeName: '某地点',
      },
      fee: 100,
      capacity: 10,
      registeredCount: 0,
      minAge: 0,
      tags: [],
      runtimeStatus: 'takenDown',
      reviewStatus: 'approved',
      isTakenDown: true,
      createdAt: iso(-30),
    },
    // 9: 报名中 - 瑜伽
    {
      id: 9,
      creatorId: 10006,
      title: '晨间瑜伽：朝阳公园草坪',
      introduction: '在朝阳公园大草坪上进行一小时的瑜伽练习，感受自然与身心的和谐。',
      safetyNotice: '请自带瑜伽垫，穿着舒适运动服装。',
      coverUrl: cover(9),
      images: [cover(9)],
      startTime: iso(6, 7, 0),
      endTime: iso(6, 9, 0),
      registrationDeadline: iso(5, 20, 0),
      location: {
        longitude: 116.478,
        latitude: 39.94,
        city: '北京',
        address: '朝阳区朝阳公园西门大草坪',
        placeName: '朝阳公园',
      },
      fee: 20,
      capacity: 25,
      registeredCount: 0,
      minAge: 14,
      tags: ['瑜伽', '运动', '户外'],
      runtimeStatus: 'registering',
      reviewStatus: 'approved',
      isTakenDown: false,
      createdAt: iso(-1),
    },
    // 10: 报名中 - 编程
    {
      id: 10,
      creatorId: 10002,
      title: '前端技术分享会：Vue 3 实战',
      introduction: '分享 Vue 3 Composition API、TypeScript 最佳实践和项目经验。',
      safetyNotice: '请携带笔记本电脑，提前安装好开发环境。',
      coverUrl: cover(10),
      images: [cover(10)],
      startTime: iso(8, 14, 0),
      endTime: iso(8, 17, 0),
      registrationDeadline: iso(7, 23, 59),
      location: {
        longitude: 116.35,
        latitude: 39.95,
        city: '北京',
        address: '海淀区知春路锦秋国际大厦 3 层会议室',
        placeName: '锦秋国际大厦',
      },
      fee: 0,
      capacity: 40,
      registeredCount: 0,
      minAge: 16,
      tags: ['编程', 'Vue', '学习'],
      runtimeStatus: 'registering',
      reviewStatus: 'approved',
      isTakenDown: false,
      createdAt: iso(-6),
    },
    // 11: 大容量活动 -> 提交审核时进入 pending
    {
      id: 11,
      creatorId: 10001,
      title: '大型户外音乐节',
      introduction: '夏日户外音乐节，多组乐队现场演出，美食市集，露营体验。',
      safetyNotice: '请携带防晒用品，注意补水，遵守现场秩序。',
      coverUrl: cover(11),
      images: [cover(11)],
      startTime: iso(30, 12, 0),
      endTime: iso(31, 22, 0),
      registrationDeadline: iso(25, 23, 59),
      location: {
        longitude: 116.5,
        latitude: 40.0,
        city: '北京',
        address: '顺义区奥林匹克水上公园',
        placeName: '奥林匹克水上公园',
      },
      fee: 199,
      capacity: 200,
      registeredCount: 0,
      minAge: 16,
      tags: ['音乐', '户外', '露营'],
      runtimeStatus: 'registering',
      reviewStatus: 'pending',
      isTakenDown: false,
      createdAt: iso(-2),
      aiContentReview: {
        status: 'succeeded',
        riskLevel: 'medium',
        suggestedReviewStatus: 'pending',
        reasons: ['活动人数超过50人，需要人工审核'],
      },
    },
    // 12: 风险审核活动
    {
      id: 12,
      creatorId: 10004,
      title: '免费抽奖活动',
      introduction: '参加即可抽奖，奖品丰厚。',
      safetyNotice: '',
      coverUrl: cover(12),
      images: [],
      startTime: iso(15, 10, 0),
      endTime: iso(15, 18, 0),
      registrationDeadline: iso(13, 23, 59),
      location: {
        longitude: 116.4,
        latitude: 39.9,
        city: '北京',
        address: '某商场',
        placeName: '某商场',
      },
      fee: 0,
      capacity: 100,
      registeredCount: 0,
      minAge: 0,
      tags: ['抽奖'],
      runtimeStatus: 'registering',
      reviewStatus: 'pending',
      isTakenDown: false,
      createdAt: iso(-1),
    },
  ]

  /* ---- 报名记录 ---- */
  const registrations: MockRegistration[] = [
    // 活动 1 (羽毛球) - 3 人报名（已满员），当前用户 10001 未报名 → 候补演示
    {
      id: 2,
      activityId: 1,
      userId: 10006,
      status: 'registered',
      registeredAt: iso(-3),
    },
    {
      id: 3,
      activityId: 1,
      userId: 10009,
      status: 'registered',
      registeredAt: iso(-2),
    },
    {
      id: 4,
      activityId: 1,
      userId: 10007,
      status: 'registered',
      registeredAt: iso(-1),
    },
    // 活动 2 (骑行) - 2 人报名
    {
      id: 5,
      activityId: 2,
      userId: 10003,
      status: 'registered',
      registeredAt: iso(-2),
    },
    {
      id: 6,
      activityId: 2,
      userId: 10001,
      status: 'registered',
      registeredAt: iso(-1),
    },
    // 活动 3 (桌游) - 3 人报名
    {
      id: 7,
      activityId: 3,
      userId: 10007,
      status: 'registered',
      registeredAt: iso(-2),
    },
    {
      id: 8,
      activityId: 3,
      userId: 10001,
      status: 'registered',
      registeredAt: iso(-1),
    },
    {
      id: 9,
      activityId: 3,
      userId: 10010,
      status: 'registered',
      registeredAt: iso(-1),
    },
    // 活动 4 (读书会) - 2 人报名
    {
      id: 10,
      activityId: 4,
      userId: 10010,
      status: 'registered',
      registeredAt: iso(-3),
    },
    {
      id: 11,
      activityId: 4,
      userId: 10006,
      status: 'registered',
      registeredAt: iso(-2),
    },
    // 活动 5 (环保徒步, 满员) - 7 人报名（capacity=7）
    {
      id: 12,
      activityId: 5,
      userId: 10002,
      status: 'registered',
      registeredAt: iso(-14),
    },
    {
      id: 13,
      activityId: 5,
      userId: 10003,
      status: 'registered',
      registeredAt: iso(-13),
    },
    {
      id: 14,
      activityId: 5,
      userId: 10006,
      status: 'registered',
      registeredAt: iso(-12),
    },
    {
      id: 15,
      activityId: 5,
      userId: 10007,
      status: 'registered',
      registeredAt: iso(-11),
    },
    // 注意：原 id:16（userId 10008）已移除，使 10008 可作为候补候选人
    {
      id: 17,
      activityId: 5,
      userId: 10009,
      status: 'registered',
      registeredAt: iso(-9),
    },
    {
      id: 18,
      activityId: 5,
      userId: 10010,
      status: 'registered',
      registeredAt: iso(-8),
    },
    {
      id: 19,
      activityId: 5,
      userId: 10004,
      status: 'canceled',
      registeredAt: iso(-14),
    },
    {
      id: 16,
      activityId: 5,
      userId: 10001,
      status: 'registered',
      registeredAt: iso(-7),
    },
    // 活动 6 (夜跑, 进行中) - 3 人
    {
      id: 22,
      activityId: 6,
      userId: 10003,
      status: 'checkedIn',
      registeredAt: iso(-6),
    },
    {
      id: 23,
      activityId: 6,
      userId: 10009,
      status: 'checkedIn',
      registeredAt: iso(-5),
    },
    {
      id: 24,
      activityId: 6,
      userId: 10001,
      status: 'registered',
      registeredAt: iso(-4),
    },
    // 活动 7 (已结束, 摄影) - 4 人
    {
      id: 25,
      activityId: 7,
      userId: 10001,
      status: 'checkedIn',
      registeredAt: iso(-18),
    },
    {
      id: 26,
      activityId: 7,
      userId: 10006,
      status: 'checkedIn',
      registeredAt: iso(-17),
    },
    {
      id: 27,
      activityId: 7,
      userId: 10010,
      status: 'registered',
      registeredAt: iso(-16),
    },
    {
      id: 28,
      activityId: 7,
      userId: 10008,
      status: 'canceled',
      registeredAt: iso(-15),
    },
    // 活动 9 (瑜伽) - 2 人
    {
      id: 29,
      activityId: 9,
      userId: 10006,
      status: 'registered',
      registeredAt: iso(-1),
    },
    {
      id: 30,
      activityId: 9,
      userId: 10008,
      status: 'registered',
      registeredAt: iso(-1),
    },
    // 活动 10 (编程) - 1 人
    {
      id: 31,
      activityId: 10,
      userId: 10001,
      status: 'registered',
      registeredAt: iso(-5),
    },
  ]

  // 计算 registeredCount（排除 canceled 状态）
  for (const reg of registrations) {
    if (reg.status !== 'canceled') {
      incReg(reg.activityId)
    }
  }
  // 填充 registeredCount
  for (const act of activities) {
    act.registeredCount = regCountMap[act.id] ?? 0
  }

  /* ---- 候补 ---- */
  const waitlist: MockWaitlistEntry[] = [
    {
      id: 1,
      activityId: 5,
      userId: 10008,
      position: 1,
      status: 'waiting',
      joinedAt: iso(-5),
    },
  ]

  /* ---- 签到记录 ---- */
  const checkins: MockCheckIn[] = [
    { id: 1, activityId: 6, userId: 10003, checkedInAt: iso(0, 19, 35) },
    { id: 2, activityId: 6, userId: 10009, checkedInAt: iso(0, 19, 40) },
    { id: 3, activityId: 7, userId: 10001, checkedInAt: iso(-3, 16, 5) },
    { id: 4, activityId: 7, userId: 10006, checkedInAt: iso(-3, 16, 10) },
  ]

  /* ---- 评价 ---- */
  const reviews: MockReview[] = [
    {
      id: 1,
      activityId: 7,
      userId: 10006,
      rating: 4,
      content: '活动组织得很好，就是人稍微多了一点。下次还会参加！',
      tags: ['组织有序', '氛围很好'],
      createdAt: iso(-2),
    },
  ]

  /* ---- 总结 ---- */
  const summaries: MockSummary[] = [
    {
      id: 1,
      activityId: 7,
      userId: 10001,
      title: '故宫角楼日落外拍精彩回顾',
      content: '上周六的故宫角楼外拍圆满结束！共有 3 位小伙伴参与，大家一起记录了绝美的日落时分...',
      images: [cover(7)],
      imageTags: [{ mediaId: cover(7), tags: ['合影', '场地'] }],
      createdAt: iso(-1),
    },
  ]

  /* ---- 草稿 ---- */
  const drafts: MockDraft[] = [
    {
      id: 1,
      creatorId: 10001,
      title: '周末飞盘局',
      introduction: '来一场说打就打的飞盘局吧！',
      safetyNotice: '注意防晒，多喝水。',
      coverUrl: '',
      images: [],
      startTime: iso(10, 15, 0),
      endTime: iso(10, 18, 0),
      registrationDeadline: iso(9, 23, 59),
      location: {
        longitude: 116.47,
        latitude: 39.95,
        city: '北京',
        address: '朝阳区望京 SOHO 中心广场',
        placeName: '望京 SOHO',
      },
      fee: 0,
      capacity: 20,
      minAge: 14,
      tags: ['飞盘', '运动'],
      reviewStatus: 'draft',
      sourceType: 'manual',
      createdAt: iso(-1),
      updatedAt: iso(-1),
    },
    {
      id: 2,
      creatorId: 10001,
      title: 'Python 学习小组',
      introduction: '每周一次 Python 学习分享，从基础到进阶。',
      safetyNotice: '',
      coverUrl: '',
      images: [],
      startTime: iso(14, 19, 0),
      endTime: iso(14, 21, 0),
      registrationDeadline: iso(13, 23, 59),
      location: {
        longitude: 116.34,
        latitude: 39.96,
        city: '北京',
        address: '海淀区五道口优盛大厦',
        placeName: '优盛大厦',
      },
      fee: 0,
      capacity: 15,
      minAge: 16,
      tags: ['编程', 'Python', '学习'],
      reviewStatus: 'rejected',
      sourceType: 'manual',
      createdAt: iso(-10),
      updatedAt: iso(-5),
    },
    {
      id: 3,
      creatorId: 10001,
      title: '',
      introduction: '',
      safetyNotice: '',
      coverUrl: '',
      images: [],
      startTime: '',
      endTime: '',
      registrationDeadline: '',
      location: {
        longitude: 0,
        latitude: 0,
        city: '',
        address: '',
        placeName: '',
      },
      fee: 0,
      capacity: 0,
      minAge: 0,
      tags: [],
      reviewStatus: 'draft',
      sourceType: 'template',
      createdAt: iso(0),
      updatedAt: iso(0),
    },
  ]

  /* ---- 好友关系 ---- */
  const friends: MockFriend[] = [
    {
      userId: 10001,
      friendId: 10006,
      remark: '摄影搭子',
      groupTags: ['兴趣'],
      source: 'manualRequest',
      createdAt: iso(-100),
    },
    {
      userId: 10006,
      friendId: 10001,
      remark: '',
      groupTags: [],
      source: 'manualRequest',
      createdAt: iso(-100),
    },
    {
      userId: 10001,
      friendId: 10003,
      remark: '户外大神',
      groupTags: ['户外'],
      source: 'activityParticipants',
      createdAt: iso(-50),
    },
    {
      userId: 10003,
      friendId: 10001,
      remark: '',
      groupTags: [],
      source: 'activityParticipants',
      createdAt: iso(-50),
    },
    {
      userId: 10007,
      friendId: 10010,
      remark: '',
      groupTags: [],
      source: 'team',
      createdAt: iso(-30),
    },
    {
      userId: 10010,
      friendId: 10007,
      remark: '',
      groupTags: [],
      source: 'team',
      createdAt: iso(-30),
    },
  ]

  /* ---- 好友申请 ---- */
  const friendRequests: MockFriendRequest[] = [
    {
      id: 1,
      fromUserId: 10008,
      toUserId: 10001,
      status: 'pending',
      source: 'activityParticipants',
      message: '你好！上次活动认识的朋友，加个好友吧~',
      createdAt: iso(-1),
    },
    {
      id: 2,
      fromUserId: 10001,
      toUserId: 10009,
      status: 'accepted',
      source: 'profile',
      message: '跑步搭子求认识！',
      createdAt: iso(-20),
    },
  ]

  /* ---- 关注 ---- */
  const follows: MockFollow[] = [
    // 10001 与 10003/10006 已是手动好友，不再重复互关，避免「取消关注」与好友状态混淆
    { followerId: 10001, followingId: 10008, createdAt: iso(-5) },
    { followerId: 10007, followingId: 10001, createdAt: iso(-40) },
    { followerId: 10008, followingId: 10003, createdAt: iso(-20) },
    { followerId: 10009, followingId: 10003, createdAt: iso(-15) },
    { followerId: 10010, followingId: 10001, createdAt: iso(-10) },
  ]

  /* ---- 黑名单 ---- */
  const blacklist: MockBlacklist[] = []

  /* ---- 会话 ---- */
  const conversations: MockConversation[] = [
    {
      id: 1,
      kind: 'friend',
      name: '',
      participantIds: [10001, 10006],
      lastMessage: '好的，周末见！',
      lastMessageAt: iso(-1, 18, 30),
    },
    {
      id: 2,
      kind: 'friend',
      name: '',
      participantIds: [10001, 10003],
      lastMessage: '下次骑行记得叫我',
      lastMessageAt: iso(-3, 10, 0),
    },
    {
      id: 3,
      kind: 'team',
      name: '北京户外探险队',
      participantIds: [10003, 10001, 10009],
      lastMessage: '周末徒步活动已确定！',
      lastMessageAt: iso(-1, 9, 0),
    },
    {
      id: 4,
      kind: 'team',
      name: '桌游研究社',
      participantIds: [10007, 10010],
      lastMessage: '本周六下午老地方见',
      lastMessageAt: iso(-2, 14, 0),
    },
    {
      id: 5,
      kind: 'team',
      name: '废弃小队',
      participantIds: [10004],
      lastMessage: '',
      lastMessageAt: iso(-90),
    },
  ]

  /* ---- 消息 ---- */
  const messages: MockMessage[] = [
    {
      id: 1,
      conversationId: 1,
      senderId: 10001,
      kind: 'text',
      content: '周末的羽毛球你来吗？',
      status: 'sent',
      createdAt: iso(-1, 18, 0),
    },
    {
      id: 2,
      conversationId: 1,
      senderId: 10006,
      kind: 'text',
      content: '当然来！正好新买了个球拍试试手感',
      status: 'sent',
      createdAt: iso(-1, 18, 10),
    },
    {
      id: 3,
      conversationId: 1,
      senderId: 10001,
      kind: 'text',
      content: '太好了，那就定周六下午 2 点老地方见',
      status: 'sent',
      createdAt: iso(-1, 18, 20),
    },
    {
      id: 4,
      conversationId: 1,
      senderId: 10006,
      kind: 'text',
      content: '好的，周末见！',
      status: 'sent',
      createdAt: iso(-1, 18, 30),
    },
    {
      id: 5,
      conversationId: 2,
      senderId: 10003,
      kind: 'text',
      content: '下周末有骑行活动，从国贸到颐和园',
      status: 'sent',
      createdAt: iso(-3, 9, 30),
    },
    {
      id: 6,
      conversationId: 2,
      senderId: 10001,
      kind: 'text',
      content: '下次骑行记得叫我',
      status: 'sent',
      createdAt: iso(-3, 10, 0),
    },
  ]

  /* ---- 小队 ---- */
  const teams: MockTeam[] = [
    {
      id: 1,
      name: '北京户外探险队',
      description: '热爱户外的伙伴们集合！一起探索北京周边山水。',
      coverUrl: `https://picsum.photos/seed/team1/400/200`,
      leaderId: 10003,
      joinMode: 'publicJoin',
      status: 'active',
      maxMembers: 50,
      memberCount: 3,
      conversationId: 3,
      createdAt: iso(-60),
    },
    {
      id: 2,
      name: '桌游研究社',
      description: '从卡坦岛到阿瓦隆，各类桌游深度玩家。每周固定线下聚会。',
      coverUrl: `https://picsum.photos/seed/team2/400/200`,
      leaderId: 10007,
      joinMode: 'approvalRequired',
      status: 'active',
      maxMembers: 20,
      memberCount: 2,
      conversationId: 4,
      createdAt: iso(-40),
    },
    {
      id: 3,
      name: '废弃小队',
      description: '该小队已解散。',
      coverUrl: '',
      leaderId: 10004,
      joinMode: 'publicJoin',
      status: 'dissolved',
      maxMembers: 10,
      memberCount: 0,
      conversationId: 5,
      createdAt: iso(-90),
    },
  ]

  /* ---- 小队成员 ---- */
  const teamMembers: MockTeamMember[] = [
    {
      id: 1,
      teamId: 1,
      userId: 10003,
      role: 'leader',
      joinedAt: iso(-60),
    },
    {
      id: 2,
      teamId: 1,
      userId: 10001,
      role: 'member',
      joinedAt: iso(-55),
    },
    {
      id: 3,
      teamId: 1,
      userId: 10009,
      role: 'member',
      joinedAt: iso(-40),
    },
    {
      id: 4,
      teamId: 2,
      userId: 10007,
      role: 'leader',
      joinedAt: iso(-40),
    },
    {
      id: 5,
      teamId: 2,
      userId: 10010,
      role: 'member',
      joinedAt: iso(-35),
    },
  ]

  /* ---- 小队加入申请 ---- */
  const teamJoinRequests: MockTeamJoinRequest[] = [
    {
      id: 1,
      teamId: 2,
      userId: 10008,
      status: 'pending',
      message: '我是桌游爱好者，想加入你们！',
      createdAt: iso(-2),
    },
  ]

  /* ---- 模板 ---- */
  const templates: MockTemplate[] = [
    {
      id: 1,
      name: '运动健身',
      coverUrl: `https://picsum.photos/seed/tpl1/400/225`,
      tags: ['运动', '健身'],
      defaultTitle: '运动健身活动',
      defaultIntroduction:
        '欢迎参加本次运动健身活动！请在活动开始前做好热身运动，穿着合适的运动服装和鞋子。',
    },
    {
      id: 2,
      name: '桌游派对',
      coverUrl: `https://picsum.photos/seed/tpl2/400/225`,
      tags: ['桌游', '社交'],
      defaultTitle: '桌游派对',
      defaultIntroduction:
        '来参加桌游派对吧！我们会准备多款经典桌游，无论你是新手还是老手都能找到乐趣。',
    },
    {
      id: 3,
      name: '户外徒步',
      coverUrl: `https://picsum.photos/seed/tpl3/400/225`,
      tags: ['户外', '徒步'],
      defaultTitle: '户外徒步活动',
      defaultIntroduction: '本次户外徒步活动路线已精心规划，请穿着登山鞋或运动鞋，携带足够饮用水。',
    },
    {
      id: 4,
      name: '学习分享',
      coverUrl: `https://picsum.photos/seed/tpl4/400/225`,
      tags: ['学习', '分享'],
      defaultTitle: '学习分享会',
      defaultIntroduction: '本次学习分享会将围绕指定主题展开讨论，欢迎大家积极发言、交流心得。',
    },
    {
      id: 5,
      name: '公益活动',
      coverUrl: `https://picsum.photos/seed/tpl5/400/225`,
      tags: ['公益', '志愿'],
      defaultTitle: '公益活动',
      defaultIntroduction: '感谢你的善心参与！本次公益活动旨在为社会贡献一份力量，请遵守活动规则。',
    },
  ]

  /* ---- 组装数据库 ---- */
  return {
    users,
    activities,
    drafts,
    registrations,
    waitlist,
    checkins,
    reviews,
    summaries,
    friends,
    friendRequests,
    follows,
    blacklist,
    conversations,
    messages,
    teams,
    teamMembers,
    teamJoinRequests,
    interestTags,
    templates,
    nextId: {
      users: 10011,
      activities: 13,
      drafts: 4,
      registrations: 32,
      waitlist: 3,
      checkins: 5,
      reviews: 3,
      summaries: 2,
      friendRequests: 3,
      conversations: 6,
      messages: 7,
      teams: 4,
      teamMembers: 6,
      teamJoinRequests: 2,
      interestTags: 13,
      templates: 6,
    },
  }
}

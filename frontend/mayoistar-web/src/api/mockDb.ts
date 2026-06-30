import {
  AdminUserSummary,
  MerchantProfile,
  ActivityDetail,
  TeamProfile,
  UserReport,
  ReviewRecord,
  QualificationStatus,
  ReportStatus,
} from '../types';

// Helper to generate IDs
const generateId = (prefix: string) => `${prefix}_${Math.random().toString(36).substr(2, 9)}`;

// Core static assets helpers
const MOCK_IMAGES = {
  avatar1:
    'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
  avatar2:
    'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80',
  avatar3:
    'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=150&auto=format&fit=crop&q=80',
  avatar4:
    'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150&auto=format&fit=crop&q=80',
  avatarMerchant:
    'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=150&auto=format&fit=crop&q=80',
  coverCamping:
    'https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?w=800&auto=format&fit=crop&q=80',
  coverBoardgame:
    'https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?w=800&auto=format&fit=crop&q=80',
  coverKtv:
    'https://images.unsplash.com/photo-1484755560695-a4c740285fa6?w=800&auto=format&fit=crop&q=80',
  license1:
    'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=600&auto=format&fit=crop&q=80',
  license2:
    'https://images.unsplash.com/photo-1583521214690-73421a1829a9?w=600&auto=format&fit=crop&q=80',
};

// Initial datasets
const initialUsers = [
  {
    userId: 'user_001',
    email: 'zhangsan@quju.com',
    nickname: '徒步旅人小张',
    kind: 'personal',
    status: 'active',
    activityCount: 5,
    teamCount: 1,
    createdAt: '2026-03-12T08:30:00Z',
  },
  {
    userId: 'user_002',
    email: 'lisi@quju.com',
    nickname: '桌游高玩李四',
    kind: 'personal',
    status: 'active',
    activityCount: 12,
    teamCount: 2,
    createdAt: '2026-04-01T14:20:00Z',
  },
  {
    userId: 'user_003',
    email: 'danger_guy@163.com',
    nickname: '低俗内容发布者',
    kind: 'personal',
    status: 'banned',
    activityCount: 2,
    teamCount: 0,
    createdAt: '2026-05-10T11:05:00Z',
  },
  {
    userId: 'user_004',
    email: 'youth_camp@merchant.com',
    nickname: '趣野户外俱乐部',
    kind: 'merchant',
    status: 'active',
    qualificationStatus: 'approved',
    activityCount: 24,
    teamCount: 3,
    createdAt: '2026-02-15T09:12:00Z',
  },
  {
    userId: 'user_005',
    email: 'breezy_cafe@merchant.com',
    nickname: '微风密室剧本杀',
    kind: 'merchant',
    status: 'active',
    qualificationStatus: 'pending',
    activityCount: 0,
    teamCount: 0,
    createdAt: '2026-06-20T16:45:00Z',
  },
  {
    userId: 'user_006',
    email: 'bad_merchant@merchant.com',
    nickname: '野鸡徒步代理商',
    kind: 'merchant',
    status: 'active',
    qualificationStatus: 'rejected',
    activityCount: 1,
    teamCount: 1,
    createdAt: '2026-05-01T10:00:00Z',
  },
] satisfies AdminUserSummary[];

const initialMerchants = [
  {
    userId: 'user_004',
    merchantName: '北京趣野户外文化传播有限公司',
    nickname: '趣野户外俱乐部',
    avatar: {
      mediaId: 'img_avatar_m1',
      fileName: 'avatar_m1.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 12450,
      usage: 'avatar',
      url: MOCK_IMAGES.avatarMerchant,
      uploadedAt: '2026-02-15T09:15:00Z',
    },
    interestedActivityFields: ['户外徒步', '登山露营', '亲子团建'],
    accountStatus: 'active',
    qualificationStatus: 'approved',
    qualification: {
      status: 'approved',
      submittedAt: '2026-02-15T10:00:00Z',
      reviewedAt: '2026-02-16T14:30:00Z',
      licenseImageUrls: [MOCK_IMAGES.license1],
    },
  },
  {
    userId: 'user_005',
    merchantName: '上海微风餐饮娱乐合伙企业',
    nickname: '微风密室剧本杀',
    avatar: {
      mediaId: 'img_avatar_m2',
      fileName: 'avatar_m2.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 9800,
      usage: 'avatar',
      url: MOCK_IMAGES.avatar4,
      uploadedAt: '2026-06-20T16:50:00Z',
    },
    interestedActivityFields: ['桌游卡牌', '剧本解密', '室内娱乐'],
    accountStatus: 'active',
    qualificationStatus: 'pending',
    qualification: {
      status: 'pending',
      submittedAt: '2026-06-28T10:12:00Z',
      licenseImageUrls: [MOCK_IMAGES.license2],
    },
  },
  {
    userId: 'user_006',
    merchantName: '野鸡山寨徒步代理有限公司',
    nickname: '野鸡徒步代理商',
    avatar: {
      mediaId: 'img_avatar_m3',
      fileName: 'avatar_m3.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 45000,
      usage: 'avatar',
      url: MOCK_IMAGES.avatar2,
      uploadedAt: '2026-05-01T10:05:00Z',
    },
    interestedActivityFields: ['户外徒步'],
    accountStatus: 'active',
    qualificationStatus: 'rejected',
    qualification: {
      status: 'rejected',
      submittedAt: '2026-05-01T11:00:00Z',
      reviewedAt: '2026-05-02T16:00:00Z',
      rejectReason:
        '提交的营业执照字迹模糊，水印不匹配，且涉嫌合成造假。请上传真实有效的实体执照彩色照片。',
      licenseImageUrls: [MOCK_IMAGES.license1],
    },
  },
] satisfies MerchantProfile[];

const initialActivities = [
  {
    activityId: 'act_001',
    title: '「京郊秘境」灵山草甸两日深度徒步与星空露营',
    tags: ['户外徒步', '登山露营', '星空摄影'],
    startAt: '2026-07-15T08:00:00Z',
    endAt: '2026-07-17T18:00:00Z',
    location: {
      point: { longitude: 115.421, latitude: 40.032 },
      city: '北京市',
      address: '门头沟区灵山风景区徒步大本营',
      placeName: '北京灵山自然保护区',
    },
    coverImage: {
      mediaId: 'img_cover_1',
      fileName: 'camping.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 154000,
      usage: 'activityImage',
      url: MOCK_IMAGES.coverCamping,
      uploadedAt: '2026-06-25T12:00:00Z',
    },
    feeAmount: 299,
    reviewStatus: 'approved',
    runtimeStatus: 'registering',
    registeredCount: 42,
    capacity: 100,
    introduction:
      '逃离城市的高温与喧嚣，和志同道合的伙伴们一起征服北京第一高峰！本次活动将有专业户外领队全程带队，提供全套安全装备。傍晚我们将在海拔2000米的草甸上扎营，煮热红酒，数流星，迎接日出。',
    safetyNotice:
      '1. 本次徒步属于中高强度，累计爬升1200米，不建议无运动习惯者报名。\n2. 户外天气多变，必须自备防风防水外套及徒步鞋。\n3. 保护自然环境，不遗留任何非降解垃圾。',
    registrationDeadline: '2026-07-12T18:00:00Z',
    organizerId: 'user_004',
    organizerName: '趣野户外俱乐部',
    images: [
      {
        mediaId: 'img_img1',
        fileName: 'hiking_view.jpg',
        contentType: 'image/jpeg',
        sizeBytes: 85000,
        usage: 'activityImage',
        url: MOCK_IMAGES.coverCamping,
        uploadedAt: '2026-06-25T12:01:00Z',
      },
    ],
    waitingCount: 0,
    manualReviewRequired: true, // Capacity > 50
    aiContentReview: {
      status: 'succeeded',
      riskLevel: 'low',
      suggestedReviewStatus: 'approved',
      reasons: [
        '未发现违法、低俗、高危涉密等违规文本。安全须知完整健全，符合户外大客流集聚申报规范。',
      ],
    },
    reviewRecords: [
      {
        reviewId: 'rec_001_ai',
        result: 'approved',
        reason: 'AI 自动内容合规评估：文本情绪安全，核心关键词匹配健康。',
        reviewedAt: '2026-06-25T12:05:00Z',
      },
      {
        reviewId: 'rec_001_man',
        result: 'approved',
        reason:
          '人工复核：超50人规模活动，发起商户「趣野户外」具备合格户外资质与保险采购机制，同意发布。',
        reviewerId: 'admin_001',
        reviewedAt: '2026-06-26T09:00:00Z',
      },
    ],
  },
  {
    activityId: 'act_002',
    title: '【新手友好】周末克苏鲁跑团《暗夜低语》剧本推理聚会',
    tags: ['桌游卡牌', '剧本解密', '克苏鲁'],
    startAt: '2026-07-04T13:00:00Z',
    endAt: '2026-07-04T18:00:00Z',
    location: {
      point: { longitude: 121.473, latitude: 31.23 },
      city: '上海市',
      address: '黄浦区人民广场微风密室轰趴馆3号房',
      placeName: '微风轰趴密室馆',
    },
    coverImage: {
      mediaId: 'img_cover_2',
      fileName: 'boardgame.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 110000,
      usage: 'activityImage',
      url: MOCK_IMAGES.coverBoardgame,
      uploadedAt: '2026-06-28T14:00:00Z',
    },
    feeAmount: 50,
    reviewStatus: 'pending',
    runtimeStatus: 'notStarted',
    registeredCount: 0,
    capacity: 6,
    introduction:
      '一封来自阿卡姆镇的神秘求助信，拉开了探寻古老迷雾的序幕。专业KP带跑，精美道具，全程高能沉浸式演绎，适合第一次体验TRPG跑团的新人小伙伴。',
    safetyNotice:
      '1. 涉及神秘解密和克苏鲁黑暗世界观，包含部分惊悚悬疑描述，心脑血管疾病及易受惊吓者请酌情报名。\n2. 活动场地内请勿大声喧哗，爱护正版卡牌和实体地图道具。',
    registrationDeadline: '2026-07-03T12:00:00Z',
    organizerId: 'user_001',
    organizerName: '徒步旅人小张',
    images: [],
    waitingCount: 0,
    manualReviewRequired: false,
    aiContentReview: {
      status: 'succeeded',
      riskLevel: 'medium',
      suggestedReviewStatus: 'approved',
      reasons: [
        '检测到敏感词“克苏鲁”、“低语”、“献祭”。但通过上下文分析属于纯虚构剧情剧本杀娱乐活动，无现实煽动违规，建议人工确认。',
      ],
    },
    reviewRecords: [
      {
        reviewId: 'rec_002_ai',
        result: 'pending',
        reason: 'AI 触发判定：文案中存在剧本杀特定敏感词，系统标记为中风险并推荐人工审核。',
        reviewedAt: '2026-06-28T14:10:00Z',
      },
    ],
  },
  {
    activityId: 'act_003',
    title: '【高额返现】加群下载APP即送50元红包！线下兼职充场秒结',
    tags: ['赚钱兼职', '福利返利'],
    startAt: '2026-07-10T14:00:00Z',
    endAt: '2026-07-10T17:00:00Z',
    location: {
      point: { longitude: 113.264, latitude: 23.129 },
      city: '广州市',
      address: '天河区石牌桥地铁站旁小会议室',
      placeName: '临时商旅会议中心',
    },
    coverImage: {
      mediaId: 'img_cover_3',
      fileName: 'spam.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 82000,
      usage: 'activityImage',
      url: MOCK_IMAGES.coverKtv,
      uploadedAt: '2026-06-29T02:00:00Z',
    },
    feeAmount: 0,
    reviewStatus: 'rejected',
    runtimeStatus: 'notStarted',
    registeredCount: 0,
    capacity: 200,
    introduction:
      '急招线下展会充场人员，工作简单，只需扫码加群，下载我们的最新金融客户端注册实名即可领取50元微信返现，现场咖啡免费畅饮！',
    safetyNotice: '不收取任何押金。18岁以下不招。',
    registrationDeadline: '2026-07-09T23:59:59Z',
    organizerId: 'user_003',
    organizerName: '低俗内容发布者',
    images: [],
    waitingCount: 0,
    manualReviewRequired: true,
    aiContentReview: {
      status: 'succeeded',
      riskLevel: 'high',
      suggestedReviewStatus: 'rejected',
      reasons: [
        '触发高危警报：文案中带有典型网络兼职诈骗、套现、非法金融实名绑卡特征。强烈建议直接驳回封锁。',
      ],
    },
    reviewRecords: [
      {
        reviewId: 'rec_003_ai',
        result: 'rejected',
        reason: 'AI 自动内容合规评估：检测到涉嫌非法实名绑卡充场的黑灰产诱导倾向。',
        reviewedAt: '2026-06-29T02:05:00Z',
      },
      {
        reviewId: 'rec_003_man',
        result: 'rejected',
        reason:
          '人工终核：确认为黑灰产引流广告，危害平台用户资产安全，直接予以驳回，并对发起账号执行连带封禁。',
        reviewerId: 'admin_001',
        reviewedAt: '2026-06-29T08:00:00Z',
      },
    ],
  },
  {
    activityId: 'act_004',
    title: '「野外拉练」绝望坡超级重装穿越活动（不推新）',
    tags: ['户外徒步', '重装穿越', '自负盈亏'],
    startAt: '2026-06-20T06:00:00Z',
    endAt: '2026-06-21T18:00:00Z',
    location: {
      point: { longitude: 114.057, latitude: 22.543 },
      city: '深圳市',
      address: '三水线绝望坡入口牌坊',
      placeName: '深圳三水线山道',
    },
    feeAmount: 0,
    reviewStatus: 'approved',
    runtimeStatus: 'takenDown',
    registeredCount: 4,
    capacity: 15,
    introduction:
      '绝望坡连续数十个陡峭山头连登，重装负重15kg以上，路况全为野路碎石，无任何补给点。此次为高强度拉练，谢绝所有无重装经验的新人。出了任何危险责任全自负。',
    safetyNotice: '山高路陡，常年有野猪，气温高。必须携带足够1.5L*4瓶水。签署生死免责协议。',
    registrationDeadline: '2026-06-19T18:00:00Z',
    organizerId: 'user_006',
    organizerName: '野鸡徒步代理商',
    images: [],
    waitingCount: 0,
    manualReviewRequired: false,
    reviewRecords: [
      {
        reviewId: 'rec_004_ai',
        result: 'approved',
        reason: 'AI 自动通过：未匹配国家明令禁止违禁词。',
        reviewedAt: '2026-06-15T09:00:00Z',
      },
      {
        reviewId: 'rec_004_takedown',
        result: 'rejected', // in review record mapped to rejection/takedown
        reason:
          '平台强制下架：收到多次用户举报。该活动组织方没有在林业局报备高危越野路线，强迫参与者签署违法生死免责协议规避法定义务，且领队无红十字救护员证书，涉嫌草菅人命。',
        reviewerId: 'admin_001',
        reviewedAt: '2026-06-18T11:00:00Z',
      },
    ],
  },
] satisfies ActivityDetail[];

const initialTeams = [
  {
    teamId: 'team_001',
    name: '华南野驴徒步大纵队',
    tags: ['户外徒步', '重装露营', '交友'],
    joinMode: 'approvalRequired',
    capacity: 200,
    memberCount: 142,
    description:
      '汇聚整个华南地区最硬核的野生徒步玩家！每个周末我们都在大山里扎营，不畏风雨，只为寻找未受开发的世外桃源。如果你敢于挑战极限，欢迎加入我们。',
    avatar: {
      mediaId: 'img_team_av1',
      fileName: 'team1.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 15400,
      usage: 'avatar',
      url: MOCK_IMAGES.avatar1,
      uploadedAt: '2026-03-12T09:00:00Z',
    },
    status: 'active',
    leaderId: 'user_004',
    chatId: 'chat_t1',
  },
  {
    teamId: 'team_002',
    name: '周五下班开黑桌游俱乐部',
    tags: ['桌游卡牌', '剧本解密', '面基交流'],
    joinMode: 'publicJoin',
    capacity: 50,
    memberCount: 38,
    description:
      '专门为周五下班想放松的小伙伴提供聚会桌游群。主要组织：阿瓦隆、狼人杀、璀璨宝石、卡卡颂、剧本杀。社恐友好，包教包会！',
    avatar: {
      mediaId: 'img_team_av2',
      fileName: 'team2.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 13200,
      usage: 'avatar',
      url: MOCK_IMAGES.avatar3,
      uploadedAt: '2026-04-01T15:00:00Z',
    },
    status: 'active',
    leaderId: 'user_002',
    chatId: 'chat_t2',
  },
  {
    teamId: 'team_003',
    name: '高息投资理财内部共享小队',
    tags: ['金融理财', '投资返现'],
    joinMode: 'publicJoin',
    capacity: 500,
    memberCount: 15,
    description: '内部靠谱高回报点位，日收益2%-5%首存即送大红包，专人带单稳赚不赔，扫码上车！',
    avatar: {
      mediaId: 'img_team_av3',
      fileName: 'team3.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 11200,
      usage: 'avatar',
      url: MOCK_IMAGES.avatar2,
      uploadedAt: '2026-05-10T11:20:00Z',
    },
    status: 'disabled',
    leaderId: 'user_003',
    chatId: 'chat_t3',
  },
] satisfies TeamProfile[];

const initialReports = [
  {
    reportId: 'rep_001',
    reporterUserId: 'user_001',
    targetUserId: 'user_003',
    reason:
      '该用户在多个活动评论区以及小队群聊内群发非法投资、兼职套现和绑卡返还50元红包的网贷诈骗引流广告，非常恶劣！',
    status: 'resolved',
    handlingNote:
      '后台人工复核属实。已依法在后台对该违规引流活动「【高额返现】加群下载APP即送50元红包！线下兼职充场秒结」(act_003)执行下架处理，并同步无限期封禁了发布人账号。',
    createdAt: '2026-06-29T03:00:00Z',
    handledAt: '2026-06-29T08:15:00Z',
  },
  {
    reportId: 'rep_002',
    reporterUserId: 'user_002',
    targetUserId: 'user_006',
    reason:
      '该商家发布的绝望坡拉练越野活动，存在极其严重的安全疏漏，领队无证且拒不配合林业局封山通告，强行带队走危险野道。',
    status: 'pending',
    createdAt: '2026-06-29T09:12:00Z',
  },
] satisfies UserReport[];

// Database state container supporting simple persistence
class MockDatabase {
  private users: AdminUserSummary[] = [];
  private merchants: MerchantProfile[] = [];
  private activities: ActivityDetail[] = [];
  private teams: TeamProfile[] = [];
  private reports: UserReport[] = [];
  private adminPasswordHash: string = 'admin123'; // Initial password

  constructor() {
    this.load();
  }

  private load() {
    try {
      const storedUsers = localStorage.getItem('quju_users');
      const storedMerchants = localStorage.getItem('quju_merchants');
      const storedActivities = localStorage.getItem('quju_activities');
      const storedTeams = localStorage.getItem('quju_teams');
      const storedReports = localStorage.getItem('quju_reports');
      const storedPassword = localStorage.getItem('quju_admin_pwd');

      this.users = storedUsers ? JSON.parse(storedUsers) : [...initialUsers];
      this.merchants = storedMerchants ? JSON.parse(storedMerchants) : [...initialMerchants];
      this.activities = storedActivities ? JSON.parse(storedActivities) : [...initialActivities];
      this.teams = storedTeams ? JSON.parse(storedTeams) : [...initialTeams];
      this.reports = storedReports ? JSON.parse(storedReports) : [...initialReports];
      this.adminPasswordHash = storedPassword || 'admin123';

      this.save();
    } catch {
      this.users = [...initialUsers];
      this.merchants = [...initialMerchants];
      this.activities = [...initialActivities];
      this.teams = [...initialTeams];
      this.reports = [...initialReports];
      this.adminPasswordHash = 'admin123';
    }
  }

  public save() {
    localStorage.setItem('quju_users', JSON.stringify(this.users));
    localStorage.setItem('quju_merchants', JSON.stringify(this.merchants));
    localStorage.setItem('quju_activities', JSON.stringify(this.activities));
    localStorage.setItem('quju_teams', JSON.stringify(this.teams));
    localStorage.setItem('quju_reports', JSON.stringify(this.reports));
    localStorage.setItem('quju_admin_pwd', this.adminPasswordHash);
  }

  // Admin Auth password management
  public getPasswordHash(): string {
    return this.adminPasswordHash;
  }

  public updatePassword(newPassword: string) {
    this.adminPasswordHash = newPassword;
    this.save();
  }

  // GET lists & filters
  public getUsers(filters: {
    keyword?: string;
    kind?: string;
    status?: string;
    qualificationStatus?: string;
  }) {
    return this.users.filter((u) => {
      if (filters.keyword) {
        const kw = filters.keyword.toLowerCase();
        const matchesEmail = u.email.toLowerCase().includes(kw);
        const matchesNick = u.nickname?.toLowerCase().includes(kw) || false;
        if (!matchesEmail && !matchesNick) return false;
      }
      if (filters.kind && u.kind !== filters.kind) return false;
      if (filters.status && u.status !== filters.status) return false;
      if (filters.qualificationStatus && u.qualificationStatus !== filters.qualificationStatus)
        return false;
      return true;
    });
  }

  public getMerchantProfile(userId: string): MerchantProfile | undefined {
    return this.merchants.find((m) => m.userId === userId);
  }

  public getActivities(filters: {
    keyword?: string;
    reviewStatus?: string;
    runtimeStatus?: string;
  }) {
    return this.activities.filter((act) => {
      if (filters.keyword) {
        const kw = filters.keyword.toLowerCase();
        const matchesTitle = act.title.toLowerCase().includes(kw);
        const matchesOrganizer = act.organizerName.toLowerCase().includes(kw);
        if (!matchesTitle && !matchesOrganizer) return false;
      }
      if (filters.reviewStatus && act.reviewStatus !== filters.reviewStatus) return false;
      if (filters.runtimeStatus && act.runtimeStatus !== filters.runtimeStatus) return false;
      return true;
    });
  }

  public getActivityDetail(activityId: string): ActivityDetail | undefined {
    return this.activities.find((a) => a.activityId === activityId);
  }

  public getTeams(filters: { keyword?: string; status?: string }) {
    return this.teams.filter((t) => {
      if (filters.keyword) {
        const kw = filters.keyword.toLowerCase();
        const matchesName = t.name.toLowerCase().includes(kw);
        const matchesTags = t.tags.some((tag) => tag.toLowerCase().includes(kw));
        if (!matchesName && !matchesTags) return false;
      }
      if (filters.status && t.status !== filters.status) return false;
      return true;
    });
  }

  public getReports(filters: { status?: string; reporterUserId?: string; targetUserId?: string }) {
    return this.reports.filter((r) => {
      if (filters.status && r.status !== filters.status) return false;
      if (filters.reporterUserId && r.reporterUserId !== filters.reporterUserId) return false;
      if (filters.targetUserId && r.targetUserId !== filters.targetUserId) return false;
      return true;
    });
  }

  // Mutative Admin operations
  public banUser(
    userId: string,
    _reason: string,
    _bannedUntil: string,
  ): AdminUserSummary | undefined {
    const user = this.users.find((u) => u.userId === userId);
    if (user) {
      user.status = 'banned';
      this.save();
      return user;
    }
    return undefined;
  }

  public unbanUser(userId: string): AdminUserSummary | undefined {
    const user = this.users.find((u) => u.userId === userId);
    if (user) {
      user.status = 'active';
      this.save();
      return user;
    }
    return undefined;
  }

  public reviewMerchant(
    userId: string,
    approved: boolean,
    reason?: string,
  ): MerchantProfile | undefined {
    const merchant = this.merchants.find((m) => m.userId === userId);
    const user = this.users.find((u) => u.userId === userId);

    if (merchant && user) {
      const finalStatus: QualificationStatus = approved ? 'approved' : 'rejected';
      merchant.qualificationStatus = finalStatus;
      user.qualificationStatus = finalStatus;

      if (merchant.qualification) {
        merchant.qualification.status = finalStatus;
        merchant.qualification.reviewedAt = new Date().toISOString();
        if (!approved) {
          merchant.qualification.rejectReason =
            reason || '营业执照资质核验未通过，请重新上传真实的经营证明。';
        } else {
          merchant.qualification.rejectReason = undefined;
        }
      }

      this.save();
      return merchant;
    }
    return undefined;
  }

  public reviewActivity(
    activityId: string,
    result: 'approved' | 'rejected' | 'changeRequired',
    reason?: string,
  ): ActivityDetail | undefined {
    const act = this.activities.find((a) => a.activityId === activityId);
    if (act) {
      act.reviewStatus = result;
      if (result === 'approved') {
        act.runtimeStatus = 'registering'; // Approved lets it active
      } else {
        act.runtimeStatus = 'notStarted';
      }

      const newRecord: ReviewRecord = {
        reviewId: generateId('rec'),
        result: result,
        reason:
          reason ||
          (result === 'approved'
            ? '人工审核：内容核对一致，符合平台运营规范。'
            : '人工审核驳回，详见处理说明。'),
        reviewerId: 'admin_001',
        reviewedAt: new Date().toISOString(),
      };
      act.reviewRecords = [newRecord, ...act.reviewRecords];

      this.save();
      return act;
    }
    return undefined;
  }

  public takeDownActivity(activityId: string, reason: string): ActivityDetail | undefined {
    const act = this.activities.find((a) => a.activityId === activityId);
    if (act) {
      act.runtimeStatus = 'takenDown';

      const newRecord: ReviewRecord = {
        reviewId: generateId('rec'),
        result: 'rejected',
        reason: `强制下架原因：${reason}`,
        reviewerId: 'admin_001',
        reviewedAt: new Date().toISOString(),
      };
      act.reviewRecords = [newRecord, ...act.reviewRecords];

      this.save();
      return act;
    }
    return undefined;
  }

  public restoreActivity(activityId: string): ActivityDetail | undefined {
    const act = this.activities.find((a) => a.activityId === activityId);
    if (act) {
      act.runtimeStatus = 'registering'; // Restore makes it open again
      act.reviewStatus = 'approved';

      const newRecord: ReviewRecord = {
        reviewId: generateId('rec'),
        result: 'approved',
        reason: '平台运营干预：撤销下架限制，活动已重新上架恢复可见。',
        reviewerId: 'admin_001',
        reviewedAt: new Date().toISOString(),
      };
      act.reviewRecords = [newRecord, ...act.reviewRecords];

      this.save();
      return act;
    }
    return undefined;
  }

  public disableTeam(teamId: string, _reason: string): TeamProfile | undefined {
    const team = this.teams.find((t) => t.teamId === teamId);
    if (team) {
      team.status = 'disabled';
      this.save();
      return team;
    }
    return undefined;
  }

  public restoreTeam(teamId: string): TeamProfile | undefined {
    const team = this.teams.find((t) => t.teamId === teamId);
    if (team) {
      team.status = 'active';
      this.save();
      return team;
    }
    return undefined;
  }

  public decideUserReport(
    reportId: string,
    status: ReportStatus,
    handlingNote: string,
  ): UserReport | undefined {
    const rpt = this.reports.find((r) => r.reportId === reportId);
    if (rpt) {
      rpt.status = status;
      rpt.handlingNote = handlingNote;
      rpt.handledAt = new Date().toISOString();
      this.save();
      return rpt;
    }
    return undefined;
  }
}

export const mockDb = new MockDatabase();
export { generateId };

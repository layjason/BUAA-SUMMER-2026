/**
 * Mock 业务工作流函数
 *
 * 每个函数读取/写入内存数据库，实现核心业务状态转换。
 * 返回值约定：
 * - 成功时返回业务数据（由 mockServer 包装为 MockApiResponse）
 * - 失败时抛出 MockBusinessError，由 mockServer 捕获并转为错误响应
 */
import {
  deliverChatRealtimeEvent,
  deliverSocialRealtimeEvent,
  type ChatRealtimeEvent,
} from './chatRealtimeBus'
import { getMockDb, persistMockDb, nextId, repairConversationStore } from './database'
import type {
  ActivityDetail,
  ActivityDraftDetail,
  ActivityDraftSummary,
  ActivityFeedTab,
  ActivityMapPoint,
  ActivityParticipant,
  ActivityParticipationState,
  ActivityReview,
  ActivityReviewListItem,
  ActivityReviewRequest,
  MyActivityReviewResult,
  MyActivitySummaryResult,
  ActivitySearchQuery,
  ActivitySummary,
  ActivitySummaryPost,
  ActivitySummaryPostRequest,
  ActivityUpsertRequest,
  ActivityTemplate,
  ChatMessage,
  CheckInQrCode,
  CheckInRecord,
  CheckInRequest,
  ConversationSummary,
  EmptyData,
  FriendItem,
  FriendRequest,
  FriendRequestCreate,
  FollowItem,
  FollowRelation,
  BlacklistItem,
  Report,
  ReportCreateRequest,
  ReportStatus,
  InterestTagItem,
  JoinTeamRequestBody,
  LocationInfo,
  LoginResult,
  MediaFile,
  MerchantProfile,
  MockDraftUpsertInput,
  NicknameAvailability,
  PublicUserProfile,
  QualificationSubmitRequest,
  RegisterActivityRequest,
  RegisteredActivitySummary,
  RegistrationResult,
  SendMessageRequest,
  TeamCreateRequest,
  TeamJoinRequest,
  TeamMember,
  TeamMemberRole,
  TeamProfile,
  UpdateMerchantProfileRequest,
  UpdatePersonalProfileRequest,
  UserKind,
} from './schema-types'
import type {
  MockUser,
  MockActivity,
  MockDraft,
  MockRegistration,
  MockPageResult,
  MockTeam,
  MockConversation,
  MockMessage,
} from './types'

/* ================================================================
 *  错误处理
 * ================================================================ */

/** Mock 业务异常，携带错误码和中文提示 */
export class MockBusinessError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'MockBusinessError'
    this.code = code
  }
}

/** 生成模拟 token */
function mockToken(prefix: string): string {
  return `mock_${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

/* ================================================================
 *  工具函数
 * ================================================================ */

/**
 * Haversine 公式计算两点间距离（米）
 *
 * 前置条件：经纬度为有效数值
 * 后置条件：返回两点间的地面距离，单位米
 */
function haversineDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6_371_000 // 地球半径，米
  const toRad = (deg: number) => (deg * Math.PI) / 180
  const dLat = toRad(lat2 - lat1)
  const dLon = toRad(lon2 - lon1)
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

/** mock 地点输入，兼容 OpenAPI LocationInfo 与旧扁平经纬度字段 */
type MockLocationInput = Partial<LocationInfo> & {
  longitude?: number
  latitude?: number
}

function normalizeDraftInput(data: MockDraftUpsertInput): MockDraftUpsertInput {
  return data
}

/**
 * 将 OpenAPI LocationInfo 转为 mockDb 内部地点结构。
 *
 * 前置条件：location 可为空，且若携带坐标则来自 point 或旧字段 longitude/latitude
 * 后置条件：返回 mockDb 可存储的扁平经纬度结构
 * 不变量：页面和 API 模块只使用 OpenAPI 字段，旧字段兼容只存在于 mock 边界内
 */
function normalizeLocation(location?: MockLocationInput): MockDraft['location'] {
  return {
    longitude: location?.point?.longitude ?? location?.longitude ?? 0,
    latitude: location?.point?.latitude ?? location?.latitude ?? 0,
    city: location?.city ?? '',
    address: location?.address ?? '',
    placeName: location?.placeName ?? '',
  }
}

/**
 * 将 mock 内部地点结构转为 OpenAPI LocationInfo。
 */
function toLocationInfo(loc: MockDraft['location']): LocationInfo {
  return {
    city: loc.city,
    address: loc.address,
    placeName: loc.placeName || undefined,
    point: { longitude: loc.longitude, latitude: loc.latitude },
  }
}

function mediaFileFromId(
  mediaId: string,
  uploadedAt: string,
  fallbackSeed: string,
  usage: MediaFile['usage'] = 'activityImage',
): MediaFile {
  const seed = encodeURIComponent(mediaId || fallbackSeed)
  const accessUrl = `https://picsum.photos/seed/${seed}/400/225`
  return {
    mediaId,
    signedUrl: accessUrl,
    contentType: 'image/jpeg',
    fileName: `${mediaId || fallbackSeed}.jpg`,
    sizeBytes: 50000,
    uploadedAt,
    usage,
  }
}

function avatarMediaFile(userId: number, url: string, uploadedAt: string): MediaFile {
  return mediaFileFromId(`media_avatar_${userId}`, uploadedAt, `avatar_${userId}`, 'avatar')
}

function mediaFilesFromActivity(a: MockActivity): MediaFile[] {
  if (a.imageIds?.length) {
    return a.imageIds.map((mediaId, index) =>
      mediaFileFromId(mediaId, a.createdAt, `activity_${a.id}_${index}`),
    )
  }
  return a.images.map((imageUrl, index) => ({
    mediaId: `media_img_${a.id}_${index}`,
    signedUrl: imageUrl,
    contentType: 'image/jpeg',
    fileName: `image_${index}.jpg`,
    sizeBytes: 50000,
    uploadedAt: a.createdAt,
    usage: 'activityImage',
  }))
}

function mediaFilesFromDraft(d: MockDraft): MediaFile[] {
  if (d.imageIds?.length) {
    return d.imageIds.map((mediaId, index) =>
      mediaFileFromId(mediaId, d.updatedAt, `draft_${d.id}_${index}`),
    )
  }
  return d.images.map((imageUrl, index) => ({
    mediaId: `media_draft_${d.id}_${index}`,
    signedUrl: imageUrl,
    contentType: 'image/jpeg',
    fileName: `draft_image_${index}.jpg`,
    sizeBytes: 50000,
    uploadedAt: d.updatedAt,
    usage: 'activityImage',
  }))
}

/** 获取当前用户 ID（mock 环境下默认使用第一个活跃个人用户） */
let currentUserId: number = 10001

/**
 * 设置当前登录用户
 *
 * @param userId 用户标识
 */
export function setCurrentUserId(userId: number): void {
  currentUserId = userId
}

/** 获取当前登录用户 ID */
export function getCurrentUserId(): number {
  return currentUserId
}

/* ================================================================
 *  1. 登录
 * ================================================================ */

/**
 * 邮箱密码登录
 *
 * 前置条件：email、password 非空
 * 后置条件：成功返回 token 和用户信息；失败抛出对应错误码
 */
export function login(email: string, password: string): LoginResult {
  const db = getMockDb()
  const user = db.users.find((u) => u.email === email)

  if (!user || user.password !== password) {
    throw new MockBusinessError(10001, '邮箱或密码错误')
  }
  if (user.accountStatus === 'inactive') {
    throw new MockBusinessError(10004, '账号尚未激活，请查收激活邮件')
  }
  if (user.accountStatus === 'banned') {
    throw new MockBusinessError(10005, '账号已被封禁')
  }

  // 切换当前用户
  setCurrentUserId(user.id)

  const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
  return {
    userId: String(user.id),
    kind: user.kind,
    accountStatus: user.accountStatus,
    tokens: {
      accessToken: mockToken('access'),
      refreshToken: mockToken('refresh'),
      expiresAt,
    },
  }
}

/* ================================================================
 *  2. 注册
 * ================================================================ */

/**
 * 注册用户
 *
 * 前置条件：email 未被注册，nickname 全平台唯一
 * 后置条件：创建 inactive 状态用户，返回成功
 */
export function register(
  email: string,
  password: string,
  nickname: string,
  kind: UserKind,
): EmptyData {
  const db = getMockDb()

  if (db.users.some((u) => u.email === email)) {
    throw new MockBusinessError(10001, '该邮箱已被注册')
  }
  if (db.users.some((u) => u.nickname === nickname)) {
    throw new MockBusinessError(10002, '该昵称已被使用')
  }

  const id = nextId('users')
  const user: MockUser = {
    id,
    email,
    password,
    nickname,
    avatarUrl: `https://picsum.photos/seed/avatar${id}/128/128`,
    kind,
    accountStatus: 'inactive',
    gender: 'unknown',
    birthday: '',
    signature: '',
    interestTagIds: [],
    createdAt: new Date().toISOString(),
  }
  db.users.push(user)
  persistMockDb()
  return {}
}

/* ================================================================
 *  3. 信息流
 * ================================================================ */

/**
 * 获取首页活动信息流
 *
 * 前置条件：tab ∈ {recommended, latest, nearby}
 * 后置条件：返回分页的活动列表，仅包含已审核通过且未下架的活动
 */
export function getFeed(
  tab: ActivityFeedTab,
  page: number,
  pageSize: number,
  filters: ActivitySearchQuery = {},
): MockPageResult<ActivitySummary> {
  const db = getMockDb()

  // 筛选可见活动
  const visible = db.activities.filter(
    (a) => a.reviewStatus === 'approved' && !a.isTakenDown && a.runtimeStatus !== 'takenDown',
  )
  const filtered = applyActivitySearchFilters(visible, filters)

  // 排序
  if (tab === 'latest') {
    filtered.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  } else if (tab === 'nearby') {
    const mockLat = filters.latitude ?? 39.908
    const mockLon = filters.longitude ?? 116.46
    filtered.sort((a, b) => {
      const distA = haversineDistance(mockLat, mockLon, a.location.latitude, a.location.longitude)
      const distB = haversineDistance(mockLat, mockLon, b.location.latitude, b.location.longitude)
      return distA - distB
    })
  } else {
    // recommended: 随机加权（模拟推荐算法）
    filtered.sort(() => Math.random() - 0.5)
  }

  return paginate(
    filtered.map((a) => activityToFeedItem(db, a)),
    page,
    pageSize,
  )
}

/** 读取活动是否要求签到位置校验；mock 未配置时默认 false */
function readRequireLocationCheck(activity: { requireLocationCheck?: boolean }): boolean {
  return activity.requireLocationCheck ?? false
}

/** 活动转为信息流摘要 */
function activityToFeedItem(db: ReturnType<typeof getMockDb>, a: MockActivity): ActivitySummary {
  return {
    activityId: String(a.id),
    title: a.title,
    startAt: a.startTime,
    endAt: a.endTime,
    capacity: a.capacity,
    registeredCount: countRegisteredParticipants(db, a.id),
    occupiedCount: computeOccupiedCount(db, a.id),
    feeAmount: a.fee,
    reviewStatus: a.reviewStatus,
    runtimeStatus: a.runtimeStatus,
    tags: a.tags,
    location: toLocationInfo(a.location),
    coverImage: mediaFilesFromActivity(a)[0],
    requireLocationCheck: readRequireLocationCheck(a),
  }
}

/**
 * 根据活动构建审核记录列表
 *
 * 前置条件：activity 存在
 * 后置条件：返回该活动的审核记录列表
 *
 * @param a MockActivity 对象
 * @returns 审核记录列表
 */
function buildReviewRecords(a: MockActivity): Array<{
  reviewId: string
  result: 'pending' | 'approved' | 'rejected' | 'changeRequired'
  reason?: string
  reviewedAt: string
  reviewerId?: string
}> {
  // 如果已有存储的审核记录，直接返回
  if (a.reviewRecords && a.reviewRecords.length > 0) {
    return a.reviewRecords
  }

  // 根据 aiContentReview 构造审核记录
  if (a.aiContentReview) {
    return [
      {
        reviewId: `auto-${a.id}`,
        result: a.aiContentReview.suggestedReviewStatus,
        reason: a.aiContentReview.reasons.join('; '),
        reviewedAt: a.createdAt,
      },
    ]
  }

  // 根据 reviewStatus 构造默认审核记录
  if (a.reviewStatus === 'approved') {
    return [
      {
        reviewId: `auto-${a.id}`,
        result: 'approved' as const,
        reason: 'AI 自动审核通过',
        reviewedAt: a.createdAt,
      },
    ]
  }

  if (a.reviewStatus === 'pending') {
    return [
      {
        reviewId: `auto-${a.id}`,
        result: 'pending' as const,
        reason: '活动已提交审核，等待处理',
        reviewedAt: a.createdAt,
      },
    ]
  }

  return []
}

/* ================================================================
 *  4. 活动详情
 * ================================================================ */

/**
 * 获取活动详情
 *
 * 前置条件：activityId 存在
 * 后置条件：返回完整活动信息
 */
export function getActivityDetail(activityId: number): ActivityDetail {
  const db = getMockDb()
  const a = db.activities.find((act) => act.id === activityId)
  if (!a) {
    throw new MockBusinessError(20002, `活动 ${activityId} 不存在或不可见`)
  }

  const creator = db.users.find((u) => u.id === a.creatorId)
  const waitingCount = db.waitlist.filter(
    (w) =>
      w.activityId === activityId && (w.status === 'waiting' || w.status === 'waitingConfirmation'),
  ).length

  return {
    activityId: String(a.id),
    title: a.title,
    introduction: a.introduction,
    safetyNotice: a.safetyNotice,
    startAt: a.startTime,
    endAt: a.endTime,
    registrationDeadline: a.registrationDeadline,
    capacity: a.capacity,
    registeredCount: countRegisteredParticipants(db, activityId),
    occupiedCount: computeOccupiedCount(db, activityId),
    waitingCount,
    feeAmount: a.fee,
    reviewStatus: a.reviewStatus,
    runtimeStatus: a.runtimeStatus,
    manualReviewRequired: Boolean(a.aiContentReview) || a.capacity > 50,
    tags: a.tags,
    location: toLocationInfo(a.location),
    coverImage: mediaFilesFromActivity(a)[0],
    images: mediaFilesFromActivity(a),
    organizerId: String(a.creatorId),
    organizerName: creator?.nickname ?? '未知',
    reviewRecords: buildReviewRecords(a),
    aiContentReview: a.aiContentReview ?? undefined,
    requireLocationCheck: readRequireLocationCheck(a),
  }
}

/* ================================================================
 *  5. 参与状态
 * ================================================================ */

/**
 * 统计活动已确认报名人数
 *
 * 前置条件：db 已初始化
 * 后置条件：仅统计 registered 与 checkedIn 状态
 */
function countRegisteredParticipants(db: ReturnType<typeof getMockDb>, activityId: number): number {
  return db.registrations.filter(
    (r) => r.activityId === activityId && (r.status === 'registered' || r.status === 'checkedIn'),
  ).length
}

/**
 * 统计候补待确认占用的名额数
 *
 * 前置条件：db 已初始化
 * 后置条件：仅统计 waitingConfirmation 状态
 */
function countPendingConfirmationSeats(
  db: ReturnType<typeof getMockDb>,
  activityId: number,
): number {
  return db.waitlist.filter(
    (w) => w.activityId === activityId && w.status === 'waitingConfirmation',
  ).length
}

/**
 * 计算活动已占用名额（正式报名 + 候补待确认预留）
 *
 * 前置条件：db 已初始化
 * 后置条件：返回值用于满员判断，与详情页展示逻辑一致
 * 不变量：返回值 >= 已确认报名人数
 */
function computeOccupiedCount(db: ReturnType<typeof getMockDb>, activityId: number): number {
  return countRegisteredParticipants(db, activityId) + countPendingConfirmationSeats(db, activityId)
}

/**
 * 判断活动是否已满员
 *
 * 前置条件：activity 存在
 * 后置条件：occupiedCount >= capacity 时返回 true
 */
function isActivityFull(db: ReturnType<typeof getMockDb>, activity: MockActivity): boolean {
  return computeOccupiedCount(db, activity.id) >= activity.capacity
}

/**
 * 获取用户在活动中的参与状态
 *
 * 前置条件：activityId 和 userId 有效
 * 后置条件：返回报名/候补状态或 null
 */
export function getParticipationState(
  activityId: number,
  userId: number,
): ActivityParticipationState {
  const db = getMockDb()

  const reg = db.registrations.find(
    (r) => r.activityId === activityId && r.userId === userId && r.status !== 'canceled',
  )
  const waitEntry = db.waitlist.find(
    (w) =>
      w.activityId === activityId &&
      w.userId === userId &&
      (w.status === 'waiting' || w.status === 'waitingConfirmation'),
  )

  const activity = db.activities.find((a) => a.id === activityId)
  const isApproved = activity?.reviewStatus === 'approved'
  const isNotTakenDown = activity ? !activity.isTakenDown : false
  const isRegistering = activity?.runtimeStatus === 'registering'
  const isDeadlinePassed = activity ? new Date(activity.registrationDeadline) < new Date() : true
  const registrationOpen = isApproved && isNotTakenDown && isRegistering && !isDeadlinePassed

  const canRegister = registrationOpen && !reg && !waitEntry
  const canCancel =
    (reg?.status === 'registered' ||
      waitEntry?.status === 'waiting' ||
      waitEntry?.status === 'waitingConfirmation') &&
    isRegistering &&
    !isDeadlinePassed
  const canCheckIn = reg?.status === 'registered' && activity?.runtimeStatus === 'ongoing'
  const canConfirmWaiting = waitEntry?.status === 'waitingConfirmation'
  const reviewWindowEndsAt = activity
    ? new Date(new Date(activity.endTime).getTime() + 7 * 24 * 60 * 60 * 1000).toISOString()
    : undefined
  const hasReviewed = db.reviews.some((r) => r.activityId === activityId && r.userId === userId)
  const canReview =
    reg?.status === 'checkedIn' &&
    activity?.runtimeStatus === 'ended' &&
    !hasReviewed &&
    Boolean(reviewWindowEndsAt && new Date(reviewWindowEndsAt).getTime() > Date.now())

  const status: ActivityParticipationState['status'] = reg
    ? reg.status
    : waitEntry?.status === 'waiting' || waitEntry?.status === 'waitingConfirmation'
      ? waitEntry.status
      : undefined

  return {
    status,
    canRegister,
    canCancelRegistration: canCancel,
    canCheckIn,
    canConfirmWaitingSeat: canConfirmWaiting,
    canReview,
    reviewWindowEndsAt: canReview ? reviewWindowEndsAt : undefined,
    waitingRank: waitEntry?.position,
    confirmationDeadline: canConfirmWaiting
      ? new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString()
      : undefined,
  }
}

/* ================================================================
 *  6. 报名活动
 * ================================================================ */

/**
 * 报名活动
 *
 * 前置条件：用户未报名、活动存在且未下架
 * 后置条件：创建报名记录，registeredCount +1，满员时提示候补
 */
export function registerForActivity(
  activityId: number,
  userId: number,
  request: RegisterActivityRequest,
): RegistrationResult {
  const db = getMockDb()

  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, `活动 ${activityId} 不存在`)
  }
  if (activity.isTakenDown) {
    throw new MockBusinessError(20002, '活动已下架')
  }
  if (!request.acceptedSafetyNotice) {
    throw new MockBusinessError(20010, '活动安全须知未确认')
  }

  const existing = db.registrations.find(
    (r) => r.activityId === activityId && r.userId === userId && r.status !== 'canceled',
  )
  if (existing) {
    throw new MockBusinessError(20007, '你已报名或正在候补该活动')
  }

  const isFull = isActivityFull(db, activity)
  if (isFull) {
    return joinWaitlist(activityId, userId)
  }

  // 正式报名
  const regId = nextId('registrations')
  const reg: MockRegistration = {
    id: regId,
    activityId,
    userId,
    status: 'registered',
    registeredAt: new Date().toISOString(),
  }
  db.registrations.push(reg)
  activity.registeredCount += 1
  persistMockDb()

  return {
    activityId: String(activityId),
    registrationId: String(regId),
    status: 'registered',
  }
}

/* ================================================================
 *  7. 取消报名
 * ================================================================ */

/**
 * 取消报名
 *
 * 前置条件：用户已报名且非 canceled 状态
 * 后置条件：报名状态置为 canceled，registeredCount -1，触发候补递补
 */
export function cancelRegistration(activityId: number, userId: number): RegistrationResult {
  const db = getMockDb()

  const reg = db.registrations.find(
    (r) =>
      r.activityId === activityId &&
      r.userId === userId &&
      (r.status === 'registered' || r.status === 'waitingConfirmation'),
  )
  const waitEntry = db.waitlist.find(
    (w) =>
      w.activityId === activityId &&
      w.userId === userId &&
      (w.status === 'waiting' || w.status === 'waitingConfirmation'),
  )
  if (!reg && !waitEntry) {
    throw new MockBusinessError(20011, '当前用户没有可取消的报名或候补记录')
  }

  if (reg) {
    reg.status = 'canceled'
  }
  if (waitEntry) {
    waitEntry.status = 'expired'
  }
  const activity = db.activities.find((a) => a.id === activityId)
  if (reg && activity && activity.registeredCount > 0) {
    activity.registeredCount -= 1
  }

  // 候补递补：找到第一个 waiting 状态的候补，变为 waitingConfirmation
  const firstWaiter = db.waitlist
    .filter((w) => w.activityId === activityId && w.status === 'waiting')
    .sort((a, b) => a.position - b.position)[0]
  if (reg && firstWaiter) {
    firstWaiter.status = 'waitingConfirmation'
  }

  persistMockDb()
  return {
    activityId: String(activityId),
    registrationId: String(reg?.id ?? waitEntry?.id ?? ''),
    status: 'canceled',
  }
}

/* ================================================================
 *  8. 加入候补
 * ================================================================ */

/**
 * 加入候补队列
 *
 * 前置条件：用户未在候补中、活动满员
 * 后置条件：创建候补记录
 */
export function joinWaitlist(activityId: number, userId: number): RegistrationResult {
  const db = getMockDb()

  // 校验活动存在
  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, '活动不存在')
  }
  // 校验活动未下架
  if (activity.isTakenDown) {
    throw new MockBusinessError(20002, '活动已下架，无法加入候补')
  }
  // 校验活动已满员（含候补待确认预留名额）
  if (!isActivityFull(db, activity)) {
    throw new MockBusinessError(20004, '活动未满，无需加入候补')
  }
  // 校验用户未报名该活动（非取消状态的报名记录）
  const hasReg = db.registrations.some(
    (r) => r.activityId === activityId && r.userId === userId && r.status !== 'canceled',
  )
  if (hasReg) {
    throw new MockBusinessError(20007, '已报名该活动，无需加入候补')
  }

  const existing = db.waitlist.find(
    (w) =>
      w.activityId === activityId &&
      w.userId === userId &&
      (w.status === 'waiting' || w.status === 'waitingConfirmation'),
  )
  if (existing) {
    throw new MockBusinessError(20007, '你已在候补队列中')
  }

  const maxPosition = db.waitlist
    .filter((w) => w.activityId === activityId)
    .reduce((max, w) => Math.max(max, w.position), 0)

  const waitId = nextId('waitlist')
  db.waitlist.push({
    id: waitId,
    activityId,
    userId,
    position: maxPosition + 1,
    status: 'waiting',
    joinedAt: new Date().toISOString(),
  })
  persistMockDb()

  return {
    activityId: String(activityId),
    registrationId: String(waitId),
    waitingRank: maxPosition + 1,
    status: 'waiting',
  }
}

/* ================================================================
 *  9. 确认候补
 * ================================================================ */

/**
 * 确认候补名额
 *
 * 前置条件：用户处于 waitingConfirmation 状态
 * 后置条件：候补变为 confirmed，创建正式报名，registeredCount +1
 */
export function confirmWaitlist(activityId: number, userId: number): RegistrationResult {
  const db = getMockDb()

  const waitEntry = db.waitlist.find(
    (w) => w.activityId === activityId && w.userId === userId && w.status === 'waitingConfirmation',
  )
  if (!waitEntry) {
    throw new MockBusinessError(20012, '候补确认不可用')
  }

  waitEntry.status = 'confirmed'

  const regId = nextId('registrations')
  db.registrations.push({
    id: regId,
    activityId,
    userId,
    status: 'registered',
    registeredAt: new Date().toISOString(),
  })

  const activity = db.activities.find((a) => a.id === activityId)
  if (activity) {
    activity.registeredCount += 1
  }

  persistMockDb()
  return {
    activityId: String(activityId),
    registrationId: String(regId),
    status: 'registered',
  }
}

/* ================================================================
 *  10. 创建草稿
 * ================================================================ */

/**
 * 创建活动草稿
 *
 * 前置条件：creatorId 有效
 * 后置条件：创建新草稿并持久化
 */
export function createDraft(creatorId: number, data: MockDraftUpsertInput): ActivityDraftDetail {
  const db = getMockDb()
  const now = new Date().toISOString()
  const id = nextId('drafts')
  const input = normalizeDraftInput(data)
  const imageIds = input.imageIds ?? []

  const draft: MockDraft = {
    id,
    creatorId,
    title: input.title ?? '',
    introduction: input.introduction ?? '',
    safetyNotice: input.safetyNotice ?? '',
    coverUrl: input.coverUrl ?? '',
    images:
      input.images ??
      imageIds.map((mediaId) => mediaFileFromId(mediaId, now, mediaId).signedUrl as string),
    imageIds,
    startTime: input.startAt ?? input.startTime ?? '',
    endTime: input.endAt ?? input.endTime ?? '',
    registrationDeadline: input.registrationDeadline ?? '',
    location: normalizeLocation(input.location),
    fee: input.feeAmount ?? input.fee ?? 0,
    feeDescription: input.feeDescription,
    capacity: input.capacity ?? 0,
    minAge: input.minAge ?? 0,
    tags: input.tags ?? [],
    requireLocationCheck: input.requireLocationCheck ?? false,
    reviewStatus: 'draft',
    sourceType: input.sourceType ?? 'manual',
    createdAt: now,
    updatedAt: now,
  }
  db.drafts.push(draft)
  persistMockDb()

  return draftToResponse(draft)
}

/* ================================================================
 *  11. 更新草稿
 * ================================================================ */

/**
 * 更新活动草稿
 *
 * 前置条件：draftId 存在且属于当前用户
 * 后置条件：草稿字段被更新
 */
export function updateDraft(draftId: number, data: MockDraftUpsertInput): ActivityDraftDetail {
  const db = getMockDb()
  const draft = db.drafts.find((d) => d.id === draftId)
  if (!draft) {
    const activity = db.activities.find(
      (a) =>
        a.id === draftId && (a.reviewStatus === 'rejected' || a.reviewStatus === 'changeRequired'),
    )
    if (!activity) {
      throw new MockBusinessError(20004, '草稿不存在')
    }
    if (activity.creatorId !== currentUserId) {
      throw new MockBusinessError(20003, '无权操作该活动')
    }
    const input = normalizeDraftInput(data)
    if (input.title !== undefined) activity.title = input.title
    if (input.introduction !== undefined) activity.introduction = input.introduction
    if (input.safetyNotice !== undefined) activity.safetyNotice = input.safetyNotice
    if (input.coverUrl !== undefined) activity.coverUrl = input.coverUrl
    if (input.imageIds !== undefined) {
      activity.imageIds = input.imageIds
      activity.images = input.imageIds.map(
        (mediaId) => mediaFileFromId(mediaId, activity.createdAt, mediaId).signedUrl as string,
      )
    } else if (input.images !== undefined) {
      activity.images = input.images
    }
    if (input.startAt !== undefined || input.startTime !== undefined) {
      activity.startTime = input.startAt ?? input.startTime ?? ''
    }
    if (input.endAt !== undefined || input.endTime !== undefined) {
      activity.endTime = input.endAt ?? input.endTime ?? ''
    }
    if (input.registrationDeadline !== undefined) {
      activity.registrationDeadline = input.registrationDeadline
    }
    if (input.location !== undefined) activity.location = normalizeLocation(input.location)
    if (input.feeAmount !== undefined || input.fee !== undefined) {
      activity.fee = input.feeAmount ?? input.fee ?? 0
    }
    if (input.feeDescription !== undefined) activity.feeDescription = input.feeDescription
    if (input.capacity !== undefined) activity.capacity = input.capacity
    if (input.minAge !== undefined) activity.minAge = input.minAge
    if (input.tags !== undefined) activity.tags = input.tags
    if (input.requireLocationCheck !== undefined) {
      activity.requireLocationCheck = input.requireLocationCheck
    }
    persistMockDb()
    return activityToDraftResponse(activity)
  }
  if (draft.creatorId !== currentUserId) {
    throw new MockBusinessError(20003, '无权操作该草稿')
  }

  const input = normalizeDraftInput(data)
  if (input.title !== undefined) draft.title = input.title
  if (input.introduction !== undefined) draft.introduction = input.introduction
  if (input.safetyNotice !== undefined) draft.safetyNotice = input.safetyNotice
  if (input.coverUrl !== undefined) draft.coverUrl = input.coverUrl
  if (input.imageIds !== undefined) {
    draft.imageIds = input.imageIds
    draft.images = input.imageIds.map(
      (mediaId) => mediaFileFromId(mediaId, draft.updatedAt, mediaId).signedUrl as string,
    )
  } else if (input.images !== undefined) {
    draft.images = input.images
  }
  if (input.startAt !== undefined || input.startTime !== undefined) {
    draft.startTime = input.startAt ?? input.startTime ?? ''
  }
  if (input.endAt !== undefined || input.endTime !== undefined) {
    draft.endTime = input.endAt ?? input.endTime ?? ''
  }
  if (input.registrationDeadline !== undefined)
    draft.registrationDeadline = input.registrationDeadline
  if (input.location !== undefined) draft.location = normalizeLocation(input.location)
  if (input.feeAmount !== undefined || input.fee !== undefined)
    draft.fee = input.feeAmount ?? input.fee ?? 0
  if (input.feeDescription !== undefined) draft.feeDescription = input.feeDescription
  if (input.capacity !== undefined) draft.capacity = input.capacity
  if (input.minAge !== undefined) draft.minAge = input.minAge
  if (input.tags !== undefined) draft.tags = input.tags
  if (input.requireLocationCheck !== undefined) {
    draft.requireLocationCheck = input.requireLocationCheck
  }
  draft.updatedAt = new Date().toISOString()
  persistMockDb()

  return draftToResponse(draft)
}

/* ================================================================
 *  12. 提交活动审核
 * ================================================================ */

/**
 * 提交活动审核
 *
 * 前置条件：草稿存在，或同 ID 活动处于 rejected/changeRequired 且归当前用户所有。
 * 后置条件：草稿转为活动，或驳回/需修改活动原地重新提交，并根据规则决定审核状态。
 */
export function submitActivity(draftId: number): ActivityDetail {
  const db = getMockDb()
  const draftIdx = db.drafts.findIndex((d) => d.id === draftId)
  if (draftIdx === -1) {
    const activity = db.activities.find(
      (a) =>
        a.id === draftId && (a.reviewStatus === 'rejected' || a.reviewStatus === 'changeRequired'),
    )
    if (!activity) {
      throw new MockBusinessError(20004, '草稿不存在')
    }
    if (activity.creatorId !== currentUserId) {
      throw new MockBusinessError(20003, '无权操作该活动')
    }
    if (!activity.title || !activity.startTime || !activity.endTime || !activity.location.city) {
      throw new MockBusinessError(20005, '活动信息不完整，请补充后再提交')
    }

    const now = new Date().toISOString()
    let reviewStatus: MockActivity['reviewStatus'] = 'approved'
    let aiContentReview: MockActivity['aiContentReview'] | undefined = undefined
    const riskKeywords = ['危险', '违规', '低俗', '违法', '暴力']
    const textToCheck = (activity.title ?? '') + (activity.introduction ?? '')
    const matchedKeywords = riskKeywords.filter((kw) => textToCheck.includes(kw))
    if (matchedKeywords.length > 0) {
      reviewStatus = 'pending'
      aiContentReview = {
        status: 'succeeded',
        riskLevel: 'high',
        suggestedReviewStatus: 'pending',
        reasons: [`内容包含敏感关键词（${matchedKeywords.join('、')}），需要人工审核`],
      }
    }
    if (activity.capacity > 50) {
      reviewStatus = 'pending'
      if (!aiContentReview) {
        aiContentReview = {
          status: 'succeeded',
          riskLevel: 'medium',
          suggestedReviewStatus: 'pending',
          reasons: ['活动人数超过50人，需要人工审核'],
        }
      } else {
        aiContentReview.reasons.push('活动人数超过50人，需要人工审核')
      }
    }

    activity.reviewStatus = reviewStatus
    activity.runtimeStatus = 'registering'
    activity.aiContentReview = aiContentReview
    activity.isTakenDown = false
    activity.reviewRecords = (() => {
      if (aiContentReview) {
        return [
          {
            reviewId: `auto-${activity.id}-${Date.now()}`,
            result: aiContentReview.suggestedReviewStatus,
            reason: aiContentReview.reasons.join('; '),
            reviewedAt: now,
          },
        ]
      }
      return [
        {
          reviewId: `auto-${activity.id}-${Date.now()}`,
          result: 'approved' as const,
          reason: 'AI 自动审核通过',
          reviewedAt: now,
        },
      ]
    })()
    persistMockDb()
    return getActivityDetail(activity.id)
  }
  const draft = db.drafts[draftIdx]
  if (draft.creatorId !== currentUserId) {
    throw new MockBusinessError(20003, '无权操作该草稿')
  }

  // 基本完整性检查
  if (!draft.title || !draft.startTime || !draft.endTime || !draft.location.city) {
    throw new MockBusinessError(20005, '活动信息不完整，请补充后再提交')
  }

  // 决定审核状态，构建 AI 内容审核结果
  let reviewStatus: MockActivity['reviewStatus'] = 'approved'
  let aiContentReview: MockActivity['aiContentReview'] | undefined = undefined
  // 简单敏感词检测
  const riskKeywords = ['危险', '违规', '低俗', '违法', '暴力']
  const textToCheck = (draft.title ?? '') + (draft.introduction ?? '')
  const matchedKeywords = riskKeywords.filter((kw) => textToCheck.includes(kw))
  if (matchedKeywords.length > 0) {
    reviewStatus = 'pending'
    aiContentReview = {
      status: 'succeeded',
      riskLevel: 'high',
      suggestedReviewStatus: 'pending',
      reasons: [`内容包含敏感关键词（${matchedKeywords.join('、')}），需要人工审核`],
    }
  }
  if (draft.capacity > 50) {
    reviewStatus = 'pending'
    if (!aiContentReview) {
      aiContentReview = {
        status: 'succeeded',
        riskLevel: 'medium',
        suggestedReviewStatus: 'pending',
        reasons: ['活动人数超过50人，需要人工审核'],
      }
    } else {
      aiContentReview.reasons.push('活动人数超过50人，需要人工审核')
    }
  }

  const actId = nextId('activities')
  const now = new Date().toISOString()
  const activity: MockActivity = {
    id: actId,
    creatorId: draft.creatorId,
    title: draft.title,
    introduction: draft.introduction,
    safetyNotice: draft.safetyNotice,
    coverUrl: draft.coverUrl,
    images: draft.images,
    imageIds: draft.imageIds,
    startTime: draft.startTime,
    endTime: draft.endTime,
    registrationDeadline: draft.registrationDeadline,
    location: draft.location,
    fee: draft.fee,
    feeDescription: draft.feeDescription,
    capacity: draft.capacity,
    registeredCount: 0,
    minAge: draft.minAge,
    tags: draft.tags,
    runtimeStatus: 'registering',
    reviewStatus,
    isTakenDown: false,
    createdAt: now,
    aiContentReview,
    requireLocationCheck: draft.requireLocationCheck ?? false,
    reviewRecords: (() => {
      if (aiContentReview) {
        return [
          {
            reviewId: `auto-${actId}`,
            result: aiContentReview.suggestedReviewStatus,
            reason: aiContentReview.reasons.join('; '),
            reviewedAt: now,
          },
        ]
      }
      if (reviewStatus === 'approved') {
        return [
          {
            reviewId: `auto-${actId}`,
            result: 'approved' as const,
            reason: 'AI 自动审核通过',
            reviewedAt: now,
          },
        ]
      }
      return []
    })(),
  }
  db.activities.push(activity)
  // 从草稿列表移除
  db.drafts.splice(draftIdx, 1)
  persistMockDb()

  return getActivityDetail(actId)
}

/* ================================================================
 *  13. 生成签到二维码
 * ================================================================ */

/**
 * 生成签到二维码
 *
 * 前置条件：活动存在且调用方为发起人
 * 后置条件：返回短期有效二维码 token
 */
export function generateCheckInQrCode(activityId: number): CheckInQrCode {
  const db = getMockDb()
  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, '活动不存在')
  }
  if (activity.creatorId !== currentUserId) {
    throw new MockBusinessError(20003, '只有活动发起人可以生成签到码')
  }

  const qrCodeToken = `qr_${activityId}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  return {
    activityId: String(activityId),
    qrCodeToken,
    expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
  }
}

/* ================================================================
 *  14. 签到
 * ================================================================ */

/**
 * 扫码签到
 *
 * 前置条件：用户已报名且未签到
 * 后置条件：创建签到记录，报名状态变为 checkedIn
 */
export function checkIn(
  activityId: number,
  userId: number,
  request: CheckInRequest,
): CheckInRecord {
  const db = getMockDb()

  if (!request.qrCodeToken.trim()) {
    throw new MockBusinessError(20013, '签到二维码无效')
  }

  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, '活动不存在')
  }
  assertCheckInLocation(activity, request)

  const reg = db.registrations.find(
    (r) => r.activityId === activityId && r.userId === userId && r.status !== 'canceled',
  )
  if (!reg) {
    throw new MockBusinessError(20010, '你尚未报名该活动')
  }
  if (reg.status === 'checkedIn') {
    throw new MockBusinessError(20012, '你已签到')
  }

  const user = db.users.find((u) => u.id === userId)

  reg.status = 'checkedIn'
  const checkInId = nextId('checkins')
  const checkedInAt = new Date().toISOString()
  db.checkins.push({
    id: checkInId,
    activityId,
    userId,
    checkedInAt,
  })
  persistMockDb()

  return {
    registrationId: String(reg.id),
    userId: String(userId),
    nickname: user?.nickname ?? '未知',
    registrationStatus: 'checkedIn',
    checkedInAt,
  }
}

/* ================================================================
 *  15. 发送好友申请
 * ================================================================ */

/**
 * 发送好友申请
 *
 * 前置条件：双方不是好友且不存在待处理申请
 * 后置条件：创建好友申请记录
 */
export function sendFriendRequest(fromUserId: number, request: FriendRequestCreate): FriendRequest {
  const db = getMockDb()
  const toUserId = Number(request.targetUserId)
  const source = request.source
  const message = request.message

  if (!Number.isFinite(toUserId) || toUserId <= 0) {
    throw new MockBusinessError(40000, '用户不存在或不可见')
  }
  if (!db.friendRequests) db.friendRequests = []
  if (!db.friends) db.friends = []
  if (!db.blacklist) db.blacklist = []

  if (hasBlacklistRelation(db, fromUserId, toUserId)) {
    throw new MockBusinessError(40001, '因黑名单关系无法进行此操作')
  }

  // 检查是否已经是好友
  const alreadyFriends = db.friends.some((f) => f.userId === fromUserId && f.friendId === toUserId)
  if (alreadyFriends) {
    throw new MockBusinessError(40004, '好友关系状态不允许此操作')
  }

  // 检查是否有待处理的申请（双向）
  const pendingReq = db.friendRequests.find(
    (r) =>
      r.status === 'pending' &&
      ((r.fromUserId === fromUserId && r.toUserId === toUserId) ||
        (r.fromUserId === toUserId && r.toUserId === fromUserId)),
  )
  if (pendingReq) {
    if (pendingReq.fromUserId === toUserId && pendingReq.toUserId === fromUserId) {
      throw new MockBusinessError(40006, '对方已向你发送好友申请，请到好友申请中处理')
    }
    throw new MockBusinessError(40006, '好友申请已存在')
  }

  const reqId = nextId('friendRequests')
  const createdAt = new Date().toISOString()
  const record = {
    id: reqId,
    fromUserId,
    toUserId,
    status: 'pending' as const,
    source,
    message: message ?? '',
    createdAt,
  }
  db.friendRequests.push(record)
  persistMockDb()

  const result = {
    requestId: String(reqId),
    requesterId: String(fromUserId),
    targetUserId: String(toUserId),
    status: 'pending' as const,
    source,
    message: message || undefined,
    createdAt,
  }
  deliverSocialRealtimeEvent(toUserId, result)
  return result
}

/* ================================================================
 *  16. 处理好友申请
 * ================================================================ */

/**
 * 处理好友申请（接受或拒绝）
 *
 * 前置条件：申请存在且接收方为当前用户
 * 后置条件：接受则双向创建好友记录；拒绝则标记申请状态
 */
export function handleFriendRequest(requestId: number, accept: boolean): FriendRequest {
  const db = getMockDb()
  const req = db.friendRequests.find((r) => r.id === requestId)
  if (!req) {
    throw new MockBusinessError(40005, '好友申请状态不允许此操作')
  }
  if (req.status !== 'pending') {
    throw new MockBusinessError(40005, '好友申请状态不允许此操作')
  }

  if (accept) {
    req.status = 'accepted'
    const now = new Date().toISOString()
    db.friends.push({
      userId: req.fromUserId,
      friendId: req.toUserId,
      remark: '',
      groupTags: [],
      source: req.source,
      createdAt: now,
    })
    db.friends.push({
      userId: req.toUserId,
      friendId: req.fromUserId,
      remark: '',
      groupTags: [],
      source: req.source,
      createdAt: now,
    })

    ensureFriendConversation(db, req.fromUserId, req.toUserId, now)
  } else {
    req.status = 'rejected'
  }

  persistMockDb()
  return {
    requestId: String(requestId),
    requesterId: String(req.fromUserId),
    targetUserId: String(req.toUserId),
    status: req.status,
    source: req.source as FriendRequest['source'],
    message: req.message || undefined,
    createdAt: req.createdAt,
  }
}

/** 查找两人之间的好友私聊会话 */
function findFriendConversation(
  conversations: MockConversation[],
  userId: number,
  friendId: number,
): MockConversation | undefined {
  return conversations.find(
    (c) =>
      c.kind === 'friend' &&
      c.participantIds.length === 2 &&
      c.participantIds.includes(userId) &&
      c.participantIds.includes(friendId),
  )
}

/** 确保好友私聊会话存在（幂等） */
function ensureFriendConversation(
  db: ReturnType<typeof getMockDb>,
  userId: number,
  friendId: number,
  createdAt?: string,
): MockConversation {
  const existing = findFriendConversation(db.conversations, userId, friendId)
  if (existing) return existing

  const now = createdAt ?? new Date().toISOString()
  const conv: MockConversation = {
    id: nextId('conversations'),
    kind: 'friend',
    name: '',
    participantIds: [userId, friendId],
    lastMessage: '',
    lastMessageAt: now,
  }
  db.conversations.push(conv)
  return conv
}

/** 统计当前用户在会话中的未读消息数 */
function countUnreadMessages(
  messages: ReturnType<typeof getMockDb>['messages'],
  userId: number,
  conversationId: number,
): number {
  return messages.filter(
    (m) =>
      m.conversationId === conversationId &&
      m.senderId !== userId &&
      m.status !== 'recalled' &&
      !(m.readBy ?? []).includes(userId),
  ).length
}

/**
 * 对方是否已读本人消息（对齐 OpenAPI peerReadStatus）
 *
 * 判定顺序：
 * 1. readBy 显式包含对方 userId（对方调用 markMessagesRead）
 * 2. 对方在此消息之后发过回复 → 隐含已读（回复前必然看过）
 */
function hasPeerReadMessage(
  peerId: number,
  msg: MockMessage,
  convMessages: MockMessage[],
): boolean {
  if ((msg.readBy ?? []).includes(peerId)) return true

  const msgTime = new Date(msg.createdAt).getTime()
  return convMessages.some(
    (m) =>
      m.senderId === peerId && m.status !== 'recalled' && new Date(m.createdAt).getTime() > msgTime,
  )
}

function resolvePeerReadStatus(
  conv: MockConversation,
  msg: MockMessage,
  viewerId: number,
  convMessages: MockMessage[],
): ChatMessage['peerReadStatus'] | undefined {
  if (msg.senderId !== viewerId || msg.status === 'recalled') return undefined
  if (conv.kind !== 'friend') return undefined
  const peers = conv.participantIds.filter((id) => id !== viewerId)
  if (peers.length === 0) return undefined
  return peers.every((peer) => hasPeerReadMessage(peer, msg, convMessages)) ? 'read' : 'unread'
}

/** 将 readerId 加入消息 readBy（幂等），返回是否新标记为已读 */
function addMessageReader(msg: MockMessage, readerId: number): boolean {
  const readBy = msg.readBy ?? []
  if (readBy.includes(readerId)) return false
  msg.readBy = [...readBy, readerId]
  return true
}

/** 打开会话 / 发消息时：将对方发来的消息全部标为当前用户已读 */
function markIncomingMessagesRead(
  db: ReturnType<typeof getMockDb>,
  conversationId: number,
  readerId: number,
): MockMessage[] {
  const newlyRead: MockMessage[] = []
  db.messages
    .filter(
      (m) =>
        m.conversationId === conversationId && m.senderId !== readerId && m.status !== 'recalled',
    )
    .forEach((m) => {
      if (addMessageReader(m, readerId)) {
        newlyRead.push(m)
      }
    })
  return newlyRead
}

function makeChatRealtimeEvent(
  kind: ChatRealtimeEvent['kind'],
  conversationId: number,
  payload: ChatRealtimeEvent['payload'],
): ChatRealtimeEvent {
  return {
    kind,
    conversationId: String(conversationId),
    payload,
    occurredAt: new Date().toISOString(),
  }
}

function emitMessageCreated(
  conv: MockConversation,
  msg: MockMessage,
  senderId: number,
  db: ReturnType<typeof getMockDb>,
): void {
  const convMessages = db.messages.filter((m) => m.conversationId === conv.id)
  for (const recipientId of conv.participantIds) {
    if (recipientId === senderId) continue
    const event = makeChatRealtimeEvent('messageCreated', conv.id, {
      message: buildChatMessageDto(conv, msg, recipientId, convMessages),
      conversationUnreadCount: countUnreadMessages(db.messages, recipientId, conv.id),
    })
    deliverChatRealtimeEvent(recipientId, event)
  }
}

function emitPeerReadEvents(
  conv: MockConversation,
  newlyReadMessages: MockMessage[],
  readerId: number,
): void {
  if (conv.kind !== 'friend') return
  for (const msg of newlyReadMessages) {
    if (msg.senderId === readerId) continue
    const event = makeChatRealtimeEvent('messagePeerRead', conv.id, {
      conversationId: String(conv.id),
      messageId: String(msg.id),
      peerReadStatus: 'read',
    })
    deliverChatRealtimeEvent(msg.senderId, event)
  }
}

function emitMessageRecalled(
  conv: MockConversation,
  msg: MockMessage,
  actorId: number,
  db: ReturnType<typeof getMockDb>,
): void {
  const convMessages = db.messages.filter((m) => m.conversationId === conv.id)
  for (const recipientId of conv.participantIds) {
    if (recipientId === actorId) continue
    const event = makeChatRealtimeEvent('messageRecalled', conv.id, {
      message: buildChatMessageDto(conv, msg, recipientId, convMessages),
    })
    deliverChatRealtimeEvent(recipientId, event)
  }
}

function emitMessageForwarded(
  conv: MockConversation,
  msg: MockMessage,
  forwarderId: number,
  db: ReturnType<typeof getMockDb>,
): void {
  const convMessages = db.messages.filter((m) => m.conversationId === conv.id)
  for (const recipientId of conv.participantIds) {
    if (recipientId === forwarderId) continue
    const event = makeChatRealtimeEvent('messageForwarded', conv.id, {
      message: buildChatMessageDto(conv, msg, recipientId, convMessages),
      conversationUnreadCount: countUnreadMessages(db.messages, recipientId, conv.id),
    })
    deliverChatRealtimeEvent(recipientId, event)
  }
}

/** Mock 消息 → OpenAPI ChatMessage */
function buildChatMessageDto(
  conv: MockConversation,
  msg: MockMessage,
  viewerId: number,
  convMessages: MockMessage[],
): ChatMessage {
  const readStatus =
    msg.senderId === viewerId || (msg.readBy ?? []).includes(viewerId)
      ? ('read' as const)
      : ('unread' as const)

  const dto: ChatMessage = {
    messageId: String(msg.id),
    conversationId: String(msg.conversationId),
    senderId: String(msg.senderId),
    kind: msg.kind,
    sentAt: msg.createdAt,
    recalled: msg.status === 'recalled',
    readStatus,
  }

  const peerReadStatus = resolvePeerReadStatus(conv, msg, viewerId, convMessages)
  if (peerReadStatus) dto.peerReadStatus = peerReadStatus

  if (msg.status === 'sent') {
    if (msg.kind === 'text') dto.text = msg.content
    if (msg.kind === 'image') {
      const mediaId = msg.content.replace(/^\[image:/, '').replace(/\]$/, '')
      if (mediaId) {
        dto.image = mediaFileFromId(mediaId, msg.createdAt, mediaId, 'chatImage')
      }
    }
    if (msg.kind === 'location') {
      if (msg.locationSnapshot) {
        dto.location = {
          point: {
            longitude: msg.locationSnapshot.longitude,
            latitude: msg.locationSnapshot.latitude,
          },
          city: msg.locationSnapshot.city,
          address: msg.locationSnapshot.address,
          placeName: msg.locationSnapshot.placeName,
        }
      } else {
        const place = msg.content.replace(/^\[location:/, '').replace(/\]$/, '')
        dto.location = {
          point: { longitude: 0, latitude: 0 },
          city: '',
          address: place,
          placeName: place,
        }
      }
    }
    if (msg.mentionedUserIds?.length) {
      dto.mentionedUserIds = msg.mentionedUserIds.map(String)
    }
    if (msg.mentionAll) {
      dto.mentionAll = true
    }
  }

  return dto
}

/** 根据最新消息刷新会话摘要（撤回后同步列表预览） */
function refreshConversationPreview(conversationId: number): void {
  const db = getMockDb()
  const conv = db.conversations.find((c) => c.id === conversationId)
  if (!conv) return

  const sorted = db.messages
    .filter((m) => m.conversationId === conversationId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())

  const latest = sorted[0]
  if (!latest) {
    conv.lastMessage = ''
    return
  }

  if (latest.status === 'recalled') {
    conv.lastMessage = '[消息已撤回]'
  } else if (latest.kind === 'text') {
    conv.lastMessage = latest.content
  } else {
    conv.lastMessage = `[${latest.kind === 'image' ? '图片' : '位置'}]`
  }
  conv.lastMessageAt = latest.createdAt
}

/* ================================================================
 *  17. 发送消息
 * ================================================================ */

/**
 * 发送聊天消息
 *
 * 前置条件：会话存在且 senderId 在参与者列表中
 * 后置条件：创建消息记录，更新会话最新消息
 */
export function sendMessage(
  conversationId: number,
  senderId: number,
  request: SendMessageRequest,
): ChatMessage {
  const db = getMockDb()
  const { kind } = request

  const conv = db.conversations.find((c) => c.id === conversationId)
  if (!conv) {
    throw new MockBusinessError(50004, '会话不存在')
  }
  if (!conv.participantIds.includes(senderId)) {
    throw new MockBusinessError(50005, '你不是该会话的成员')
  }
  if (conv.kind === 'team') {
    const team = db.teams.find((t) => t.conversationId === conversationId)
    if (team && team.status !== 'active') {
      throw new MockBusinessError(40011, 'Team is unavailable')
    }
  }

  if (conv.kind === 'team') {
    const team = db.teams.find((t) => t.conversationId === conversationId)
    if (team) {
      if (request.mentionAll) {
        const operator = db.teamMembers.find((m) => m.teamId === team.id && m.userId === senderId)
        if (!operator || (operator.role !== 'leader' && operator.role !== 'admin')) {
          throw new MockBusinessError(50002, 'Team membership is required')
        }
      }
      if (request.mentionedUserIds?.length) {
        for (const mentionedId of request.mentionedUserIds) {
          const memberId = Number(mentionedId)
          const isMember = db.teamMembers.some((m) => m.teamId === team.id && m.userId === memberId)
          if (!isMember) {
            throw new MockBusinessError(50002, 'Team membership is required')
          }
        }
      }
    }
  } else if (request.mentionAll || request.mentionedUserIds?.length) {
    throw new MockBusinessError(50006, 'Message content does not match message kind')
  }

  const content =
    kind === 'text'
      ? (request.text ?? '')
      : kind === 'image'
        ? `[image:${request.imageMediaId ?? ''}]`
        : `[location:${request.location?.placeName ?? request.location?.address ?? ''}]`

  const msgId = nextId('messages')
  const now = new Date().toISOString()
  const mentionedUserIds = request.mentionedUserIds
    ?.map((id) => Number(id))
    .filter((id) => !Number.isNaN(id))
  db.messages.push({
    id: msgId,
    conversationId,
    senderId,
    kind,
    content,
    status: 'sent',
    createdAt: now,
    locationSnapshot:
      kind === 'location' && request.location
        ? {
            longitude: request.location.point.longitude,
            latitude: request.location.point.latitude,
            city: request.location.city,
            address: request.location.address,
            placeName: request.location.placeName,
          }
        : undefined,
    mentionedUserIds: mentionedUserIds?.length ? mentionedUserIds : undefined,
    mentionAll: request.mentionAll || undefined,
  })

  conv.lastMessage = kind === 'text' ? content : `[${kind === 'image' ? '图片' : '位置'}]`
  conv.lastMessageAt = now

  // 发送消息隐含已读对方此前所有消息（回复前必然看过）
  const newlyRead = markIncomingMessagesRead(db, conversationId, senderId)

  persistMockDb()

  const convMessages = db.messages.filter((m) => m.conversationId === conversationId)
  const saved = db.messages.find((m) => m.id === msgId)!
  emitMessageCreated(conv, saved, senderId, db)
  emitPeerReadEvents(conv, newlyRead, senderId)
  return buildChatMessageDto(conv, saved, senderId, convMessages)
}

/* ================================================================
 *  18. 撤回消息
 * ================================================================ */

/**
 * 撤回消息
 *
 * 前置条件：用户是发送者且发送不超过 2 分钟
 * 后置条件：消息状态变为 recalled
 */
export function recallMessage(messageId: number, userId: number): ChatMessage {
  const db = getMockDb()
  const msg = db.messages.find((m) => m.id === messageId)
  if (!msg) {
    throw new MockBusinessError(50006, '消息不存在')
  }
  if (msg.senderId !== userId) {
    throw new MockBusinessError(50007, '只能撤回自己发送的消息')
  }

  const sentTime = new Date(msg.createdAt).getTime()
  if (Date.now() - sentTime > 2 * 60 * 1000) {
    throw new MockBusinessError(50008, '消息已超过 2 分钟，无法撤回')
  }

  msg.status = 'recalled'
  refreshConversationPreview(msg.conversationId)
  persistMockDb()

  const conv = db.conversations.find((c) => c.id === msg.conversationId)
  if (conv) {
    emitMessageRecalled(conv, msg, userId, db)
    const convMessages = db.messages.filter((m) => m.conversationId === msg.conversationId)
    return buildChatMessageDto(conv, msg, userId, convMessages)
  }

  return {
    messageId: String(msg.id),
    conversationId: String(msg.conversationId),
    senderId: String(msg.senderId),
    kind: msg.kind,
    sentAt: msg.createdAt,
    recalled: true,
    readStatus: 'read',
  }
}

/* ================================================================
 *  19. 创建小队
 * ================================================================ */

/**
 * 创建小队
 *
 * 前置条件：leaderId 有效
 * 后置条件：创建小队和队长成员记录
 */
export function createTeam(leaderId: number, data: TeamCreateRequest): TeamProfile {
  const db = getMockDb()
  const teamId = nextId('teams')
  const now = new Date().toISOString()

  // 创建小队专属群聊会话
  const convId = nextId('conversations')
  db.conversations.push({
    id: convId,
    kind: 'team',
    name: data.name,
    participantIds: [leaderId],
    lastMessage: '',
    lastMessageAt: now,
  })

  const team: MockTeam = {
    id: teamId,
    name: data.name,
    description: data.description ?? '',
    coverUrl: data.avatarMediaId
      ? (mediaFileFromId(data.avatarMediaId, now, `team_${teamId}`, 'teamAlbum')
          .signedUrl as string)
      : '',
    leaderId,
    joinMode: data.joinMode,
    status: 'active',
    maxMembers: data.capacity,
    memberCount: 1,
    tags: data.tags,
    conversationId: convId,
    createdAt: now,
  }
  db.teams.push(team)
  db.teamMembers.push({
    id: nextId('teamMembers'),
    teamId,
    userId: leaderId,
    role: 'leader',
    points: 0,
    joinedAt: now,
  })
  persistMockDb()

  return teamToProfile(team)
}

/* ================================================================
 *  20. 加入小队
 * ================================================================ */

/**
 * 加入小队
 *
 * 前置条件：小队存在且未解散/未满员
 * 后置条件：公开小队直接加入；审核小队创建申请
 */
export function joinTeam(
  teamId: number,
  userId: number,
  request: JoinTeamRequestBody = {},
): TeamJoinRequest {
  const message = request.message
  const db = getMockDb()
  const team = db.teams.find((t) => t.id === teamId)
  if (!team) {
    throw new MockBusinessError(40009, 'Team is not visible')
  }
  if (team.status !== 'active') {
    throw new MockBusinessError(40011, 'Team is unavailable')
  }
  if (team.memberCount >= team.maxMembers) {
    throw new MockBusinessError(40010, 'Team is full')
  }

  if (hasBlacklistRelation(db, userId, team.leaderId)) {
    throw new MockBusinessError(40001, 'Blacklist relation blocks this operation')
  }

  const alreadyMember = db.teamMembers.some((m) => m.teamId === teamId && m.userId === userId)
  if (alreadyMember) {
    throw new MockBusinessError(40012, 'Team member already exists')
  }

  const now = new Date().toISOString()

  if (team.joinMode === 'publicJoin') {
    db.teamMembers.push({
      id: nextId('teamMembers'),
      teamId,
      userId,
      role: 'member',
      points: 0,
      joinedAt: now,
    })
    team.memberCount += 1

    // 同步小队群聊会话参与者
    if (team.conversationId) {
      const conv = db.conversations.find((c) => c.id === team.conversationId)
      if (conv && !conv.participantIds.includes(userId)) {
        conv.participantIds.push(userId)
      }
    }

    persistMockDb()
    return {
      requestId: String(nextId('teamJoinRequests')),
      teamId: String(teamId),
      userId: String(userId),
      status: 'accepted',
      message: message || undefined,
      createdAt: now,
    }
  }

  const reqId = nextId('teamJoinRequests')
  db.teamJoinRequests.push({
    id: reqId,
    teamId,
    userId,
    status: 'pending',
    message: message ?? '',
    createdAt: now,
  })
  persistMockDb()
  return {
    requestId: String(reqId),
    teamId: String(teamId),
    userId: String(userId),
    status: 'pending',
    message: message || undefined,
    createdAt: now,
  }
}

/** 离开小队 */
export function leaveTeam(teamId: number, userId: number): void {
  const db = getMockDb()
  const team = db.teams.find((t) => t.id === teamId)
  if (!team) throw new MockBusinessError(40001, '小队不存在')
  if (team.leaderId === userId) {
    throw new MockBusinessError(40016, 'Team leader cannot leave the team')
  }

  db.teamMembers = db.teamMembers.filter((m) => !(m.teamId === teamId && m.userId === userId))
  team.memberCount = Math.max(0, team.memberCount - 1)

  // 同步小队群聊会话
  if (team.conversationId) {
    const conv = db.conversations.find((c) => c.id === team.conversationId)
    if (conv) {
      conv.participantIds = conv.participantIds.filter((id) => id !== userId)
    }
  }
  persistMockDb()
}

/** 解散小队 */
export function dissolveTeam(teamId: number, userId: number): void {
  const db = getMockDb()
  const team = db.teams.find((t) => t.id === teamId)
  if (!team) throw new MockBusinessError(40001, '小队不存在')
  if (team.leaderId !== userId) {
    throw new MockBusinessError(40020, 'Team operation is not allowed')
  }
  team.status = 'dissolved'
  persistMockDb()
}

/** 获取小队成员列表 */
export function getTeamMembers(
  teamId: number,
  page: number,
  pageSize: number,
): MockPageResult<TeamMember> {
  const db = getMockDb()
  const members = db.teamMembers.filter((m) => m.teamId === teamId)
  const result = members.map((m) => {
    const user = db.users.find((u) => u.id === m.userId)
    return {
      userId: String(m.userId),
      nickname: user?.nickname ?? '未知',
      avatar: user?.avatarUrl
        ? avatarMediaFile(m.userId, user.avatarUrl, user.createdAt)
        : undefined,
      role: m.role as TeamMemberRole,
      points: m.points,
      joinedAt: m.joinedAt,
    }
  })
  return paginate(result, page, pageSize)
}

/** 获取小队入队申请 */
export function getTeamJoinRequests(
  teamId: number,
  page: number,
  pageSize: number,
): MockPageResult<TeamJoinRequest> {
  const db = getMockDb()
  const requests = db.teamJoinRequests.filter((r) => r.teamId === teamId)
  const result = requests.map((r) => ({
    requestId: String(r.id),
    teamId: String(r.teamId),
    userId: String(r.userId),
    status: r.status as TeamJoinRequest['status'],
    message: r.message || undefined,
    createdAt: r.createdAt,
  }))
  return paginate(result, page, pageSize)
}

/** 处理入队申请 */
export function handleJoinRequest(requestId: number, accepted: boolean): TeamJoinRequest {
  const db = getMockDb()
  const req = db.teamJoinRequests.find((r) => r.id === requestId)
  if (!req) throw new MockBusinessError(40020, '申请不存在')

  const now = new Date().toISOString()
  req.status = accepted ? 'accepted' : 'rejected'

  if (accepted) {
    const team = db.teams.find((t) => t.id === req.teamId)
    if (team) {
      if (team.memberCount >= team.maxMembers) {
        throw new MockBusinessError(40010, 'Team is full')
      }
      db.teamMembers.push({
        id: nextId('teamMembers'),
        teamId: req.teamId,
        userId: req.userId,
        role: 'member',
        points: 0,
        joinedAt: now,
      })
      team.memberCount += 1

      // 同步小队群聊会话
      if (team.conversationId) {
        const conv = db.conversations.find((c) => c.id === team.conversationId)
        if (conv && !conv.participantIds.includes(req.userId)) {
          conv.participantIds.push(req.userId)
        }
      }
    }
  }

  persistMockDb()
  return {
    requestId: String(req.id),
    teamId: String(req.teamId),
    userId: String(req.userId),
    status: req.status as TeamJoinRequest['status'],
    message: req.message || undefined,
    createdAt: req.createdAt,
  }
}

/** 更新成员角色 */
export function updateMemberRole(
  teamId: number,
  operatorId: number,
  memberId: number,
  role: TeamMemberRole,
): TeamMember {
  const db = getMockDb()
  const operator = db.teamMembers.find((m) => m.teamId === teamId && m.userId === operatorId)
  if (!operator || operator.role !== 'leader') {
    throw new MockBusinessError(40020, 'Team operation is not allowed')
  }
  const member = db.teamMembers.find((m) => m.teamId === teamId && m.userId === memberId)
  if (!member) throw new MockBusinessError(40015, 'Team member does not exist')
  if (member.role === 'leader') {
    throw new MockBusinessError(40017, 'Team role change is invalid')
  }
  member.role = role
  persistMockDb()

  const user = db.users.find((u) => u.id === memberId)
  return {
    userId: String(member.userId),
    nickname: user?.nickname ?? '未知',
    avatar: user?.avatarUrl
      ? avatarMediaFile(member.userId, user.avatarUrl, user.createdAt)
      : undefined,
    role,
    points: 0,
    joinedAt: member.joinedAt,
  }
}

/** 标记消息已读 */
export function markMessagesRead(messageIds: number[], userId: number): ChatMessage[] {
  const db = getMockDb()
  const touchedConvs = new Set<number>()

  for (const id of messageIds) {
    const msg = db.messages.find((m) => m.id === id)
    if (!msg || msg.senderId === userId) continue

    const conv = db.conversations.find((c) => c.id === msg.conversationId)
    if (!conv?.participantIds.includes(userId)) {
      throw new MockBusinessError(50005, '你不是该会话的成员')
    }
    touchedConvs.add(msg.conversationId)
  }

  // 打开会话：将对方发来的全部消息标为已读（写入 readBy，供发送方 peerReadStatus 使用）
  const peerReadByConv = new Map<number, MockMessage[]>()
  for (const convId of touchedConvs) {
    const newlyRead = markIncomingMessagesRead(db, convId, userId)
    if (newlyRead.length > 0) {
      peerReadByConv.set(convId, newlyRead)
    }
  }

  persistMockDb()

  for (const [convId, newlyRead] of peerReadByConv) {
    const conv = db.conversations.find((c) => c.id === convId)
    if (conv) {
      emitPeerReadEvents(conv, newlyRead, userId)
    }
  }

  const results: ChatMessage[] = []
  for (const id of messageIds) {
    const msg = db.messages.find((m) => m.id === id)
    const conv = db.conversations.find((c) => c.id === msg?.conversationId)
    if (!msg || !conv) continue
    const convMessages = db.messages.filter((m) => m.conversationId === conv.id)
    results.push(buildChatMessageDto(conv, msg, userId, convMessages))
  }
  return results
}

/** 转发消息 */
export function forwardMessage(
  messageId: number,
  senderId: number,
  targetConversationIds: number[],
): ChatMessage[] {
  const db = getMockDb()
  const original = db.messages.find((m) => m.id === messageId)
  if (!original) throw new MockBusinessError(50006, '消息不存在')

  const now = new Date().toISOString()
  const forwardedMessages: ChatMessage[] = []
  for (const convId of targetConversationIds) {
    const conv = db.conversations.find((c) => c.id === convId)
    if (!conv) {
      throw new MockBusinessError(50004, '会话不存在')
    }
    if (!conv.participantIds.includes(senderId)) {
      throw new MockBusinessError(50005, '你不是该会话的成员')
    }

    const id = nextId('messages')
    const forwarded: MockMessage = {
      id,
      conversationId: convId,
      senderId,
      kind: original.kind,
      content: original.content,
      status: 'sent',
      createdAt: now,
      locationSnapshot: original.locationSnapshot,
    }
    db.messages.push(forwarded)
    refreshConversationPreview(convId)
    emitMessageForwarded(conv, forwarded, senderId, db)
    const convMessages = db.messages.filter((m) => m.conversationId === convId)
    forwardedMessages.push(buildChatMessageDto(conv, forwarded, senderId, convMessages))
  }
  persistMockDb()
  return forwardedMessages
}

/* ================================================================
 *  21. 搜索活动
 * ================================================================ */

/**
 * 搜索活动
 *
 * 前置条件：filters 已解析
 * 后置条件：返回符合筛选条件的分页活动列表
 */
export function searchActivities(
  filters: ActivitySearchQuery,
  page: number,
  pageSize: number,
): MockPageResult<ActivitySummary> {
  const db = getMockDb()

  const visible = db.activities.filter(
    (a) => a.reviewStatus === 'approved' && !a.isTakenDown && a.runtimeStatus !== 'takenDown',
  )
  const results = applyActivitySearchFilters(visible, filters)

  return paginate(
    results.map((a) => activityToFeedItem(db, a)),
    page,
    pageSize,
  )
}

/** 按 OpenAPI 搜索查询字段筛选活动
 *
 * 前置条件：activities 已经完成可见性筛选；filters 来自 URL query。
 * 后置条件：返回满足关键词、类型、城市、时间、费用和距离条件的活动集合。
 * 不变量：仅使用 OpenAPI 已声明的查询字段，runtimeStatus 仅供 mock 内部测试使用。
 */
function applyActivitySearchFilters(
  activities: MockActivity[],
  filters: ActivitySearchQuery,
): MockActivity[] {
  let results = [...activities]
  if (filters.keyword) {
    const kw = filters.keyword.toLowerCase()
    results = results.filter(
      (a) =>
        a.title.toLowerCase().includes(kw) ||
        a.introduction.toLowerCase().includes(kw) ||
        a.tags.some((t) => t.toLowerCase().includes(kw)),
    )
  }
  if (filters.activityTypes?.length) {
    const types = filters.activityTypes.map((type) => type.toLowerCase())
    results = results.filter((a) => a.tags.some((tag) => types.includes(tag.toLowerCase())))
  }
  if (filters.city) results = results.filter((a) => a.location.city === filters.city)
  if (filters.minFee !== undefined) results = results.filter((a) => a.fee >= filters.minFee!)
  if (filters.maxFee !== undefined) results = results.filter((a) => a.fee <= filters.maxFee!)
  if (filters.runtimeStatus)
    results = results.filter((a) => a.runtimeStatus === filters.runtimeStatus)
  if (filters.startAtFrom) {
    const from = new Date(filters.startAtFrom).getTime()
    results = results.filter((a) => new Date(a.startTime).getTime() >= from)
  }
  if (filters.startAtTo) {
    const to = new Date(filters.startAtTo).getTime()
    results = results.filter((a) => new Date(a.startTime).getTime() <= to)
  }
  if (
    filters.longitude !== undefined &&
    filters.latitude !== undefined &&
    filters.distanceMeters !== undefined
  ) {
    results = results.filter((a) => {
      const dist = haversineDistance(
        filters.latitude!,
        filters.longitude!,
        a.location.latitude,
        a.location.longitude,
      )
      return dist <= filters.distanceMeters!
    })
  }
  return results
}

/* ================================================================
 *  22. 地图活动
 * ================================================================ */

/**
 * 获取地图范围内的活动点位
 *
 * 前置条件：经纬度和距离有效
 * 后置条件：返回范围内的活动摘要列表
 */
export function getMapActivities(
  longitude: number,
  latitude: number,
  distanceMeters: number,
  filters: Omit<ActivitySearchQuery, 'longitude' | 'latitude' | 'distanceMeters'> = {},
): ActivityMapPoint[] {
  const page = searchActivities({ ...filters, longitude, latitude, distanceMeters }, 1, 1000)
  return page.items.map((a) => ({
    activityId: a.activityId,
    title: a.title,
    startAt: a.startAt,
    runtimeStatus: a.runtimeStatus,
    point: a.location.point,
  }))
}

/**
 * 校验签到位置是否满足活动现场要求
 *
 * 前置条件：activity 为已存在活动，request 为 OpenAPI CheckInRequest。
 * 后置条件：开启位置校验时缺少坐标或距离超过阈值会抛出业务错误。
 * 不变量：位置阈值仅影响 mock 签到校验，不改变活动地点数据。
 */
function assertCheckInLocation(activity: MockActivity, request: CheckInRequest): void {
  if (!readRequireLocationCheck(activity)) return
  if (!request.currentLocation) {
    throw new MockBusinessError(20018, '活动要求位置校验，请允许定位后再签到')
  }
  const distance = haversineDistance(
    request.currentLocation.latitude,
    request.currentLocation.longitude,
    activity.location.latitude,
    activity.location.longitude,
  )
  if (distance > 500) {
    throw new MockBusinessError(20019, '当前位置距离活动地点过远，无法签到')
  }
}

/* ================================================================
 *  辅助函数
 * ================================================================ */

/** 通用分页 */
function paginate<T>(items: T[], page: number, pageSize: number): MockPageResult<T> {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.max(1, Math.min(page, totalPages))
  const start = (safePage - 1) * pageSize
  const paged = items.slice(start, start + pageSize)
  return { items: paged, total, page: safePage, pageSize, totalPages }
}

/** 小队转为 OpenAPI TeamProfile */
function teamToProfile(team: MockTeam): TeamProfile {
  return {
    teamId: String(team.id),
    name: team.name,
    description: team.description || undefined,
    leaderId: String(team.leaderId),
    creatorId: String(team.leaderId),
    joinMode: team.joinMode,
    status: team.status,
    memberCount: team.memberCount,
    capacity: team.maxMembers,
    tags: team.tags ?? [],
    chatId: team.conversationId ? String(team.conversationId) : `conv_team_${team.id}`,
    avatar: team.coverUrl
      ? mediaFileFromId(`media_team_${team.id}`, team.createdAt, `team_${team.id}`, 'teamAlbum')
      : undefined,
  }
}

/** 好友关系来源映射为 OpenAPI FriendshipSource */
function toFriendshipSource(source: string): FriendItem['source'] {
  return source === 'mutualFollow' ? 'mutualFollow' : 'manualRequest'
}

/** 草稿转为 API 响应格式 */
function draftToResponse(d: MockDraft): ActivityDraftDetail {
  return {
    activityId: String(d.id),
    title: d.title || undefined,
    introduction: d.introduction || undefined,
    safetyNotice: d.safetyNotice || undefined,
    startAt: d.startTime || undefined,
    endAt: d.endTime || undefined,
    registrationDeadline: d.registrationDeadline || undefined,
    capacity: d.capacity || undefined,
    feeAmount: d.fee || undefined,
    feeDescription: d.feeDescription || undefined,
    minAge: d.minAge || undefined,
    tags: d.tags,
    reviewStatus: d.reviewStatus,
    location: d.location.city ? toLocationInfo(d.location) : undefined,
    requireLocationCheck: d.requireLocationCheck ?? false,
    images: mediaFilesFromDraft(d),
    createdAt: d.createdAt,
    updatedAt: d.updatedAt,
  }
}

/**
 * 活动转为草稿编辑回显响应。
 *
 * 前置条件：activity 为当前用户可编辑的 rejected 或 changeRequired 活动。
 * 后置条件：返回 OpenAPI ActivityDraftDetail 形态，供编辑页复用草稿回填逻辑。
 * 不变量：只做字段映射，不修改 mock 数据库中的活动状态。
 */
function activityToDraftResponse(activity: MockActivity): ActivityDraftDetail {
  return {
    activityId: String(activity.id),
    title: activity.title || undefined,
    introduction: activity.introduction || undefined,
    safetyNotice: activity.safetyNotice || undefined,
    startAt: activity.startTime || undefined,
    endAt: activity.endTime || undefined,
    registrationDeadline: activity.registrationDeadline || undefined,
    capacity: activity.capacity || undefined,
    feeAmount: activity.fee || undefined,
    feeDescription: activity.feeDescription || undefined,
    minAge: activity.minAge || undefined,
    tags: activity.tags,
    reviewStatus: activity.reviewStatus,
    location: activity.location.city ? toLocationInfo(activity.location) : undefined,
    requireLocationCheck: activity.requireLocationCheck ?? false,
    images: mediaFilesFromActivity(activity),
    createdAt: activity.createdAt,
    updatedAt:
      activity.reviewRecords && activity.reviewRecords.length > 0
        ? activity.reviewRecords[activity.reviewRecords.length - 1].reviewedAt
        : activity.createdAt,
  }
}

/** 获取用户的活动列表（我创建的活动） */
export function getMyActivities(
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<ActivitySummary> {
  const db = getMockDb()
  const mine = db.activities
    .filter((a) => a.creatorId === userId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .map((a) => activityToFeedItem(db, a))
  return paginate(mine, page, pageSize)
}

/** 获取用户的草稿列表 */
export function getMyDrafts(
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<ActivityDraftSummary> {
  const db = getMockDb()
  const mine = db.drafts
    .filter((d) => d.creatorId === userId)
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    .map((d) => ({
      activityId: String(d.id),
      title: d.title || undefined,
      reviewStatus: d.reviewStatus,
      createdAt: d.createdAt,
      updatedAt: d.updatedAt,
    }))
  return paginate(mine, page, pageSize)
}

/** 获取单个草稿详情；驳回/需修改活动按草稿形态回显。 */
export function getDraft(draftId: number): ActivityDraftDetail {
  const db = getMockDb()
  const draft = db.drafts.find((d) => d.id === draftId)
  if (!draft) {
    const activity = db.activities.find(
      (a) =>
        a.id === draftId && (a.reviewStatus === 'rejected' || a.reviewStatus === 'changeRequired'),
    )
    if (!activity) {
      throw new MockBusinessError(20004, '草稿不存在')
    }
    if (activity.creatorId !== currentUserId) {
      throw new MockBusinessError(20003, '无权操作该活动')
    }
    return activityToDraftResponse(activity)
  }
  return draftToResponse(draft)
}

/** 获取用户报名的活动列表 */
export function getMyRegistrations(
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<RegisteredActivitySummary> {
  const db = getMockDb()
  const myRegs = db.registrations.filter((r) => r.userId === userId && r.status !== 'canceled')
  const myWaitlist = db.waitlist.filter(
    (w) => w.userId === userId && (w.status === 'waiting' || w.status === 'waitingConfirmation'),
  )

  const items: RegisteredActivitySummary[] = []
  for (const r of myRegs) {
    const act = db.activities.find((a) => a.id === r.activityId)
    if (!act) continue
    const waitEntry = db.waitlist.find((w) => w.activityId === act.id && w.userId === userId)
    items.push({
      activityId: String(act.id),
      registrationId: String(r.id),
      registrationStatus: r.status,
      registeredAt: r.registeredAt,
      title: act.title,
      startAt: act.startTime,
      endAt: act.endTime,
      capacity: act.capacity,
      registeredCount: countRegisteredParticipants(db, act.id),
      occupiedCount: computeOccupiedCount(db, act.id),
      feeAmount: act.fee,
      reviewStatus: act.reviewStatus,
      runtimeStatus: act.runtimeStatus,
      tags: act.tags,
      location: toLocationInfo(act.location),
      coverImage: mediaFilesFromActivity(act)[0],
      waitingRank: waitEntry?.position,
      requireLocationCheck: readRequireLocationCheck(act),
    })
  }
  for (const w of myWaitlist) {
    if (items.some((item) => item.activityId === String(w.activityId))) continue
    const act = db.activities.find((a) => a.id === w.activityId)
    if (!act) continue
    const registrationStatus =
      w.status === 'waitingConfirmation' ? 'waitingConfirmation' : 'waiting'
    items.push({
      activityId: String(act.id),
      registrationId: String(w.id),
      registrationStatus,
      registeredAt: w.joinedAt,
      title: act.title,
      startAt: act.startTime,
      endAt: act.endTime,
      capacity: act.capacity,
      registeredCount: countRegisteredParticipants(db, act.id),
      occupiedCount: computeOccupiedCount(db, act.id),
      feeAmount: act.fee,
      reviewStatus: act.reviewStatus,
      runtimeStatus: act.runtimeStatus,
      tags: act.tags,
      location: toLocationInfo(act.location),
      coverImage: mediaFilesFromActivity(act)[0],
      waitingRank: w.position,
      confirmationDeadline:
        registrationStatus === 'waitingConfirmation'
          ? new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString()
          : undefined,
      requireLocationCheck: readRequireLocationCheck(act),
    })
  }
  items.sort((a, b) => new Date(b.registeredAt).getTime() - new Date(a.registeredAt).getTime())

  return paginate(items, page, pageSize)
}

/** 获取活动参与人列表 */
export function getParticipants(
  activityId: number,
  page: number,
  pageSize: number,
): MockPageResult<ActivityParticipant> {
  const db = getMockDb()
  const regs = db.registrations.filter(
    (r) => r.activityId === activityId && r.status !== 'canceled',
  )

  const items = regs.map((r) => {
    const user = db.users.find((u) => u.id === r.userId)
    const checkIn = db.checkins.find((c) => c.activityId === activityId && c.userId === r.userId)
    const waitEntry = db.waitlist.find((w) => w.activityId === activityId && w.userId === r.userId)
    return {
      userId: String(r.userId),
      nickname: user?.nickname ?? '未知',
      registrationId: String(r.id),
      registrationStatus: r.status,
      registeredAt: r.registeredAt,
      checkedInAt: checkIn?.checkedInAt,
      waitingRank: waitEntry?.position,
      avatar: user?.avatarUrl
        ? avatarMediaFile(r.userId, user.avatarUrl, user.createdAt)
        : undefined,
    }
  })

  return paginate(items, page, pageSize)
}

/** 获取签到列表 */
export function getCheckIns(
  activityId: number,
  page: number,
  pageSize: number,
): MockPageResult<CheckInRecord> {
  const db = getMockDb()
  const regs = db.registrations.filter(
    (r) => r.activityId === activityId && r.status !== 'canceled',
  )
  const items = regs.map((r) => {
    const user = db.users.find((u) => u.id === r.userId)
    const checkIn = db.checkins.find((c) => c.activityId === activityId && c.userId === r.userId)
    return {
      userId: String(r.userId),
      nickname: user?.nickname ?? '未知',
      registrationId: String(r.id),
      registrationStatus: r.status,
      checkedInAt: checkIn?.checkedInAt,
    }
  })
  const waitItems = db.waitlist
    .filter(
      (w) =>
        w.activityId === activityId &&
        (w.status === 'waiting' || w.status === 'waitingConfirmation') &&
        !items.some((item) => item.userId === String(w.userId)),
    )
    .map((w) => {
      const user = db.users.find((u) => u.id === w.userId)
      const registrationStatus: 'waiting' | 'waitingConfirmation' =
        w.status === 'waitingConfirmation' ? 'waitingConfirmation' : 'waiting'
      return {
        userId: String(w.userId),
        nickname: user?.nickname ?? '未知',
        registrationId: String(w.id),
        registrationStatus,
        checkedInAt: undefined,
      }
    })
  items.push(...waitItems)
  return paginate(items, page, pageSize)
}

/**
 * 导出签到数据为 CSV 文本。
 *
 * 前置条件：activityId 对应活动存在，调用方有权限由路由层/真实后端校验。
 * 后置条件：返回可作为二进制导出内容模拟的 CSV 字符串。
 * 不变量：导出字段均来自 OpenAPI CheckInRecord 已定义字段，不新增业务结构。
 */
export function exportCheckInsCsv(activityId: number): string {
  const records = getCheckIns(activityId, 1, 1000).items
  const header = ['registrationId', 'userId', 'nickname', 'registrationStatus', 'checkedInAt']
  const rows = records.map((record) =>
    [
      record.registrationId,
      record.userId,
      record.nickname,
      record.registrationStatus,
      record.checkedInAt ?? '',
    ]
      .map((cell) => `"${String(cell).replace(/"/g, '""')}"`)
      .join(','),
  )
  return [header.join(','), ...rows].join('\n')
}

/**
 * 将 mock 评价转为 OpenAPI ActivityReview
 *
 * @param review mock 评价记录
 * @returns OpenAPI 评价对象
 */
function reviewToResponse(review: {
  id: number
  activityId: number
  userId: number
  rating: number
  content: string
  tags: string[]
  createdAt: string
}): ActivityReview {
  return {
    reviewId: String(review.id),
    activityId: String(review.activityId),
    userId: String(review.userId),
    rating: review.rating,
    content: review.content || undefined,
    tags: review.tags,
    createdAt: review.createdAt,
  }
}

/**
 * 将 mock 总结转为 OpenAPI ActivitySummaryPost
 *
 * @param summary mock 总结记录
 * @returns OpenAPI 总结对象
 */
function summaryToResponse(summary: {
  id: number
  activityId: number
  title: string
  content: string
  images: string[]
  imageTags: Array<{ mediaId: string; tags: string[] }>
  createdAt: string
}): ActivitySummaryPost {
  return {
    summaryId: String(summary.id),
    activityId: String(summary.activityId),
    title: summary.title,
    content: summary.content,
    images: summary.images.map((img, index) => {
      if (img.startsWith('http')) {
        return {
          mediaId: `media_summary_${summary.id}_${index}`,
          signedUrl: img,
          contentType: 'image/jpeg',
          fileName: `summary_${summary.id}_${index}.jpg`,
          sizeBytes: 50000,
          uploadedAt: summary.createdAt,
          usage: 'summaryImage' as MediaFile['usage'],
        }
      }
      return mediaFileFromId(
        img,
        summary.createdAt,
        `summary_${summary.id}_${index}`,
        'summaryImage',
      )
    }),
    imageTags: summary.imageTags,
    createdAt: summary.createdAt,
  }
}

/** 获取活动评价列表 */
export function listActivityReviews(
  activityId: number,
  page: number,
  pageSize: number,
): MockPageResult<ActivityReviewListItem> {
  const db = getMockDb()
  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, `活动 ${activityId} 不存在或不可见`)
  }

  const items = db.reviews
    .filter((r) => r.activityId === activityId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .map((r) => {
      const user = db.users.find((u) => u.id === r.userId)
      return {
        ...reviewToResponse(r),
        nickname: user?.nickname ?? '未知',
      }
    })

  return paginate(items, page, pageSize)
}

/** 获取当前用户对指定活动的评价 */
export function getMyActivityReview(activityId: number, userId: number): MyActivityReviewResult {
  const db = getMockDb()
  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, `活动 ${activityId} 不存在或不可见`)
  }

  const review = db.reviews.find((r) => r.activityId === activityId && r.userId === userId)
  return review ? { review: reviewToResponse(review) } : {}
}

/** 获取活动总结列表 */
export function listActivitySummaries(
  activityId: number,
  page: number,
  pageSize: number,
): MockPageResult<ActivitySummaryPost> {
  const db = getMockDb()
  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, `活动 ${activityId} 不存在或不可见`)
  }

  const items = db.summaries
    .filter((s) => s.activityId === activityId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .map((s) => summaryToResponse(s))

  return paginate(items, page, pageSize)
}

/** 获取当前用户对指定活动发布的总结 */
export function getMyActivitySummary(activityId: number, userId: number): MyActivitySummaryResult {
  const db = getMockDb()
  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, `活动 ${activityId} 不存在或不可见`)
  }

  const summary = db.summaries.find((s) => s.activityId === activityId && s.userId === userId)
  return summary ? { summary: summaryToResponse(summary) } : {}
}

/** 创建评价 */
export function createReview(
  activityId: number,
  userId: number,
  request: ActivityReviewRequest,
): ActivityReview {
  const db = getMockDb()

  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, '活动不存在')
  }
  if (activity.runtimeStatus !== 'ended') {
    throw new MockBusinessError(20015, '活动尚未结束，不能评价')
  }

  const reg = db.registrations.find(
    (r) => r.activityId === activityId && r.userId === userId && r.status !== 'canceled',
  )
  if (!reg || reg.status !== 'checkedIn') {
    throw new MockBusinessError(20011, '未找到可评价的报名记录')
  }
  const reviewWindowEndsAt = new Date(
    new Date(activity.endTime).getTime() + 7 * 24 * 60 * 60 * 1000,
  ).toISOString()
  if (new Date(reviewWindowEndsAt).getTime() <= Date.now()) {
    throw new MockBusinessError(20015, '评价入口已关闭')
  }

  const existing = db.reviews.find((r) => r.activityId === activityId && r.userId === userId)
  if (existing) {
    throw new MockBusinessError(20016, '你已评价过该活动')
  }

  const id = nextId('reviews')
  const now = new Date().toISOString()
  const review = {
    id,
    activityId,
    userId,
    rating: request.rating,
    content: request.content ?? '',
    tags: request.tags ?? [],
    createdAt: now,
  }
  db.reviews.push(review)
  persistMockDb()

  return reviewToResponse(review)
}

/** 创建活动总结 */
export function createSummary(
  activityId: number,
  userId: number,
  request: ActivitySummaryPostRequest,
): ActivitySummaryPost {
  const db = getMockDb()
  const activity = db.activities.find((a) => a.id === activityId)
  if (!activity) {
    throw new MockBusinessError(20002, '活动不存在')
  }
  if (activity.creatorId !== userId) {
    throw new MockBusinessError(20003, '无权发布该活动总结')
  }
  if (activity.runtimeStatus !== 'ended') {
    throw new MockBusinessError(20015, '活动尚未结束，不能发布总结')
  }
  if (db.summaries.some((s) => s.activityId === activityId)) {
    throw new MockBusinessError(20020, '活动总结已存在')
  }

  const id = nextId('summaries')
  const now = new Date().toISOString()
  const summary = {
    id,
    activityId,
    userId,
    title: request.title,
    content: request.content,
    images: request.imageIds,
    imageTags: request.confirmedImageTags,
    createdAt: now,
  }
  db.summaries.push(summary)
  persistMockDb()

  return summaryToResponse(summary)
}

/** 克隆活动为草稿 */
export function cloneActivity(activityId: number, userId: number): ActivityDraftDetail {
  const db = getMockDb()
  const source = db.activities.find((a) => a.id === activityId)
  if (!source) {
    throw new MockBusinessError(20002, '源活动不存在')
  }

  const now = new Date().toISOString()
  const id = nextId('drafts')
  const draft: MockDraft = {
    id,
    creatorId: userId,
    title: source.title,
    introduction: source.introduction,
    safetyNotice: source.safetyNotice,
    coverUrl: source.coverUrl,
    images: source.images,
    startTime: '',
    endTime: '',
    registrationDeadline: '',
    location: source.location,
    fee: source.fee,
    capacity: source.capacity,
    minAge: source.minAge,
    tags: [...source.tags],
    reviewStatus: 'draft',
    sourceType: 'clone',
    createdAt: now,
    updatedAt: now,
  }
  db.drafts.push(draft)
  persistMockDb()

  return draftToResponse(draft)
}

/** 从模板创建草稿 */
export function createDraftFromTemplate(templateId: number, userId: number): ActivityDraftDetail {
  const db = getMockDb()
  const template = db.templates.find((t) => t.id === templateId)
  if (!template) {
    throw new MockBusinessError(20001, '模板不存在')
  }

  const now = new Date().toISOString()
  const id = nextId('drafts')
  const draft: MockDraft = {
    id,
    creatorId: userId,
    title: template.defaultTitle,
    introduction: template.defaultIntroduction,
    safetyNotice: template.defaultSafetyNotice,
    coverUrl: template.coverUrl,
    images: [template.coverUrl],
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
    capacity: template.defaultCapacity,
    minAge: 0,
    tags: [...template.tags],
    reviewStatus: 'draft',
    sourceType: 'template',
    createdAt: now,
    updatedAt: now,
  }
  db.drafts.push(draft)
  persistMockDb()

  return draftToResponse(draft)
}

/** 获取用户公开资料 */
export function getUserProfile(userId: number): PublicUserProfile {
  const db = getMockDb()
  const user = db.users.find((u) => u.id === userId)
  if (!user) {
    throw new MockBusinessError(10003, '用户不存在')
  }

  const tagNames = db.interestTags
    .filter((t) => user.interestTagIds.includes(t.id))
    .map((t) => t.name)

  return {
    userId: String(user.id),
    nickname: user.nickname,
    kind: user.kind,
    gender: user.gender === 'unknown' ? 'unspecified' : user.gender,
    birthday: user.birthday || undefined,
    signature: user.signature || undefined,
    interestTags: tagNames,
    reputationScore: 100,
    avatar: user.avatarUrl ? avatarMediaFile(user.id, user.avatarUrl, user.createdAt) : undefined,
  }
}

/** 更新用户资料 */
export function updateUserProfile(
  userId: number,
  data: UpdatePersonalProfileRequest,
): PublicUserProfile {
  const db = getMockDb()
  const user = db.users.find((u) => u.id === userId)
  if (!user) {
    throw new MockBusinessError(10003, '用户不存在')
  }

  if (data.nickname !== undefined && data.nickname !== user.nickname) {
    if (db.users.some((u) => u.nickname === data.nickname && u.id !== userId)) {
      throw new MockBusinessError(10002, '该昵称已被使用')
    }
    user.nickname = data.nickname
  }
  if (data.signature !== undefined) user.signature = data.signature
  if (data.birthday !== undefined) user.birthday = data.birthday
  if (data.gender !== undefined) {
    const genderMap: Record<string, MockUser['gender']> = {
      male: 'male',
      female: 'female',
      unspecified: 'unknown',
      other: 'unknown',
    }
    user.gender = genderMap[data.gender] ?? 'unknown'
  }
  if (data.interestTags !== undefined) {
    user.interestTagIds = data.interestTags
      .map((name) => db.interestTags.find((t) => t.name === name)?.id)
      .filter((id): id is number => id !== undefined)
  }

  persistMockDb()
  return getUserProfile(userId)
}

/** 更新商家资料
 *
 * 前置条件：userId 对应商家用户；data 字段来自 OpenAPI UpdateMerchantProfileRequest。
 * 后置条件：更新 mock 用户的商家资料字段并返回最新 MerchantProfile。
 * 不变量：资质审核状态不通过资料更新接口改变。
 */
export function updateMerchantProfile(
  userId: number,
  data: UpdateMerchantProfileRequest,
): MerchantProfile {
  const db = getMockDb()
  const user = db.users.find((u) => u.id === userId && u.kind === 'merchant')
  if (!user) {
    throw new MockBusinessError(10003, '商家用户不存在')
  }
  if (data.nickname !== undefined && data.nickname !== user.nickname) {
    if (db.users.some((u) => u.nickname === data.nickname && u.id !== userId)) {
      throw new MockBusinessError(10002, '该昵称已被使用')
    }
    user.nickname = data.nickname
  }
  if (data.merchantName !== undefined) user.merchantName = data.merchantName
  if (data.interestedActivityFields !== undefined) {
    user.interestedActivityFields = data.interestedActivityFields
  }
  if (data.avatarMediaId !== undefined) {
    user.avatarUrl = `https://picsum.photos/seed/${encodeURIComponent(data.avatarMediaId)}/200/200`
  }

  persistMockDb()
  return getMerchantProfile(userId)
}

/** 提交商家资质
 *
 * 前置条件：userId 对应商家用户，licenseMediaIds 非空且引用已上传媒体文件标识。
 * 后置条件：资质状态进入 pending，等待后台审核。
 * 不变量：approved 或 pending 状态下拒绝重复提交，rejected 可重新提交。
 */
export function submitMerchantQualification(
  userId: number,
  data: QualificationSubmitRequest,
): Record<string, never> {
  const db = getMockDb()
  const user = db.users.find((u) => u.id === userId && u.kind === 'merchant')
  if (!user) {
    throw new MockBusinessError(10003, '商家用户不存在')
  }
  if (!data.licenseMediaIds.length) {
    throw new MockBusinessError(10000, '请上传营业执照或营业凭证')
  }
  if (user.qualificationStatus === 'pending' || user.qualificationStatus === 'approved') {
    throw new MockBusinessError(10000, '商家资质已提交')
  }

  user.qualificationStatus = 'pending'
  user.qualificationLicenseMediaIds = [...data.licenseMediaIds]
  user.qualificationSubmittedAt = new Date().toISOString()
  user.qualificationReviewedAt = undefined
  user.qualificationRejectReason = undefined
  persistMockDb()
  return {}
}

/** 检查昵称是否可用 */
export function checkNicknameAvailability(nickname: string): NicknameAvailability {
  const db = getMockDb()
  const taken = db.users.some((u) => u.nickname === nickname)
  return { nickname, available: !taken }
}

/** 获取好友列表 */
export function getFriends(userId: number, keyword?: string): FriendItem[] {
  const db = getMockDb()
  const friendEntries = db.friends.filter((f) => f.userId === userId)
  const normalizedKeyword = keyword?.trim().toLowerCase()
  return friendEntries
    .map((f) => {
      const friend = db.users.find((u) => u.id === f.friendId)
      return {
        userId: String(f.friendId),
        nickname: friend?.nickname ?? '未知',
        remark: f.remark || undefined,
        source: toFriendshipSource(f.source),
        groupTags: f.groupTags || [],
        avatar: friend?.avatarUrl
          ? avatarMediaFile(f.friendId, friend.avatarUrl, friend.createdAt)
          : undefined,
      }
    })
    .filter((item) => {
      if (!normalizedKeyword) return true
      const nickname = item.nickname.toLowerCase()
      const remark = (item.remark ?? '').toLowerCase()
      return nickname.includes(normalizedKeyword) || remark.includes(normalizedKeyword)
    })
}

/** 1×1 透明 PNG，用于 mock 个人二维码图片响应 */
const MOCK_PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

/** mock：为当前用户生成可解析的二维码 token */
export function createMockPersonalQrToken(userId: number): string {
  return `mock-personal-qr:${userId}:${Date.now()}`
}

function parseMockPersonalQrToken(token: string): number {
  const trimmed = token.trim()
  if (trimmed.startsWith('mock-personal-qr:')) {
    const target = trimmed.split(':')[1]
    const userId = Number(target)
    if (!Number.isNaN(userId) && userId > 0) return userId
  }
  if (/^\d+$/.test(trimmed)) {
    return Number(trimmed)
  }
  throw new MockBusinessError(40021, '二维码无效或已过期')
}

/** mock：返回个人二维码 PNG 二进制 */
export function getPersonalQrCodePng(_userId: number): ArrayBuffer {
  return base64ToArrayBuffer(MOCK_PNG_BASE64)
}

/** 扫描个人二维码并发好友申请（mock / 真实契约一致） */
export function scanPersonalQrCode(
  scannerId: number,
  token: string,
  message?: string,
): FriendRequest {
  const targetUserId = parseMockPersonalQrToken(token)
  return sendFriendRequest(scannerId, {
    targetUserId: String(targetUserId),
    source: 'qrCode',
    message,
  })
}

/** 获取收到的好友申请 */
export function getReceivedFriendRequests(userId: number): FriendRequest[] {
  const db = getMockDb()
  return db.friendRequests
    .filter((r) => r.toUserId === userId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .map((r) => ({
      requestId: String(r.id),
      requesterId: String(r.fromUserId),
      targetUserId: String(r.toUserId),
      status: r.status,
      source: r.source as FriendRequest['source'],
      message: r.message || undefined,
      createdAt: r.createdAt,
    }))
}

/** 获取发送的好友申请 */
export function getSentFriendRequests(userId: number): FriendRequest[] {
  const db = getMockDb()
  return db.friendRequests
    .filter((r) => r.fromUserId === userId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .map((r) => ({
      requestId: String(r.id),
      requesterId: String(r.fromUserId),
      targetUserId: String(r.toUserId),
      status: r.status,
      source: r.source as FriendRequest['source'],
      message: r.message || undefined,
      createdAt: r.createdAt,
    }))
}

/** 判断两人是否仍为好友 */
function areFriends(
  db: ReturnType<typeof getMockDb>,
  userId: number,
  otherUserId: number,
): boolean {
  return db.friends.some((f) => f.userId === userId && f.friendId === otherUserId)
}

/** 移除好友 */
export function removeFriend(userId: number, targetUserId: number): void {
  const db = getMockDb()
  db.friends = db.friends.filter(
    (f) =>
      !(f.userId === userId && f.friendId === targetUserId) &&
      !(f.userId === targetUserId && f.friendId === userId),
  )
  persistMockDb()
}

/** 更新好友备注 */
export function updateFriendRemark(
  userId: number,
  targetUserId: number,
  remark?: string,
  groupTags?: string[],
): FriendItem {
  const db = getMockDb()
  const entry = db.friends.find((f) => f.userId === userId && f.friendId === targetUserId)
  if (!entry) {
    throw new MockBusinessError(30010, '好友不存在')
  }
  if (remark !== undefined) entry.remark = remark
  if (groupTags !== undefined) entry.groupTags = groupTags
  persistMockDb()
  const updated = getFriends(userId).find((f) => f.userId === String(targetUserId))
  if (!updated) {
    throw new MockBusinessError(30010, '好友不存在')
  }
  return updated
}

function hasBlacklistRelation(
  db: ReturnType<typeof getMockDb>,
  userA: number,
  userB: number,
): boolean {
  if (!db.blacklist) return false
  return db.blacklist.some(
    (b) =>
      (b.blockedBy === userA && b.userId === userB) ||
      (b.blockedBy === userB && b.userId === userA),
  )
}

/** 关注用户 */
export function followUser(followerId: number, followingId: number): FollowRelation {
  const db = getMockDb()
  if (followerId === followingId) {
    throw new MockBusinessError(30020, '不能关注自己')
  }
  if (hasBlacklistRelation(db, followerId, followingId)) {
    throw new MockBusinessError(40001, '因黑名单关系无法进行此操作')
  }

  const alreadyFollowing = db.follows.some(
    (f) => f.followerId === followerId && f.followingId === followingId,
  )
  const reverseFollow = db.follows.find(
    (f) => f.followerId === followingId && f.followingId === followerId,
  )

  if (alreadyFollowing) {
    return {
      targetUserId: String(followingId),
      following: true,
      mutual: !!reverseFollow,
      friendshipCreated: false,
    }
  }

  let friendshipCreated = false
  db.follows.push({ followerId, followingId, createdAt: new Date().toISOString() })

  // 检查是否形成互关 → 自动创建 mutualFollow 好友关系
  if (reverseFollow) {
    const alreadyFriends = db.friends.some(
      (f) => f.userId === followerId && f.friendId === followingId,
    )
    if (!alreadyFriends) {
      const now = new Date().toISOString()
      db.friends.push({
        userId: followerId,
        friendId: followingId,
        remark: '',
        groupTags: [],
        source: 'mutualFollow',
        createdAt: now,
      })
      db.friends.push({
        userId: followingId,
        friendId: followerId,
        remark: '',
        groupTags: [],
        source: 'mutualFollow',
        createdAt: now,
      })
      ensureFriendConversation(db, followerId, followingId, now)
      friendshipCreated = true
    }
  }

  const mutual = db.follows.some(
    (f) => f.followerId === followingId && f.followingId === followerId,
  )

  persistMockDb()
  return {
    targetUserId: String(followingId),
    following: true,
    mutual,
    friendshipCreated,
  }
}

/** 取消关注 */
export function unfollowUser(followerId: number, followingId: number): FollowRelation {
  const db = getMockDb()
  db.follows = db.follows.filter(
    (f) => !(f.followerId === followerId && f.followingId === followingId),
  )

  // 如果因互关而成为好友，同时解除 mutualFollow 好友关系
  const mutualFriendship = db.friends.find(
    (f) => f.userId === followerId && f.friendId === followingId && f.source === 'mutualFollow',
  )
  if (mutualFriendship) {
    db.friends = db.friends.filter(
      (f) =>
        !(
          (f.userId === followerId && f.friendId === followingId) ||
          (f.userId === followingId && f.friendId === followerId)
        ),
    )
  }

  const mutual = db.follows.some(
    (f) => f.followerId === followingId && f.followingId === followerId,
  )

  persistMockDb()
  return {
    targetUserId: String(followingId),
    following: false,
    mutual,
    friendshipCreated: false,
  }
}

/** 获取关注列表 */
export function getFollows(userId: number): FollowItem[] {
  const db = getMockDb()
  return db.follows
    .filter((f) => f.followerId === userId)
    .map((f) => {
      const user = db.users.find((u) => u.id === f.followingId)
      const mutual = db.follows.some(
        (r) => r.followerId === f.followingId && r.followingId === userId,
      )
      return {
        userId: String(f.followingId),
        nickname: user?.nickname ?? '未知',
        avatar: user?.avatarUrl
          ? avatarMediaFile(f.followingId, user.avatarUrl, user.createdAt)
          : undefined,
        mutual,
        followedAt: f.createdAt,
      }
    })
}

/** 获取粉丝列表 */
export function getFollowers(userId: number): FollowItem[] {
  const db = getMockDb()
  return db.follows
    .filter((f) => f.followingId === userId)
    .map((f) => {
      const user = db.users.find((u) => u.id === f.followerId)
      const mutual = db.follows.some(
        (r) => r.followerId === userId && r.followingId === f.followerId,
      )
      return {
        userId: String(f.followerId),
        nickname: user?.nickname ?? '未知',
        avatar: user?.avatarUrl
          ? avatarMediaFile(f.followerId, user.avatarUrl, user.createdAt)
          : undefined,
        mutual,
        followedAt: f.createdAt,
      }
    })
}

/** 屏蔽用户 */
export function blockUser(userId: number, targetUserId: number): void {
  const db = getMockDb()
  if (userId === targetUserId) {
    throw new MockBusinessError(30030, '不能屏蔽自己')
  }
  const exists = db.blacklist.find((b) => b.blockedBy === userId && b.userId === targetUserId)
  if (exists) return
  db.blacklist.push({
    userId: targetUserId,
    blockedBy: userId,
    blockedAt: new Date().toISOString(),
  })
  // 同时移除好友关系与关注关系
  db.friends = db.friends.filter(
    (f) =>
      !(f.userId === userId && f.friendId === targetUserId) &&
      !(f.userId === targetUserId && f.friendId === userId),
  )
  db.follows = db.follows.filter(
    (f) =>
      !(f.followerId === userId && f.followingId === targetUserId) &&
      !(f.followerId === targetUserId && f.followingId === userId),
  )
  persistMockDb()
}

/** 取消屏蔽 */
export function unblockUser(userId: number, targetUserId: number): void {
  const db = getMockDb()
  db.blacklist = db.blacklist.filter((b) => !(b.blockedBy === userId && b.userId === targetUserId))
  persistMockDb()
}

/** 获取黑名单列表 */
export function getBlacklist(userId: number): BlacklistItem[] {
  const db = getMockDb()
  return db.blacklist
    .filter((b) => b.blockedBy === userId)
    .map((b) => {
      const user = db.users.find((u) => u.id === b.userId)
      return {
        userId: String(b.userId),
        nickname: user?.nickname ?? '未知',
        avatar: user?.avatarUrl
          ? avatarMediaFile(b.userId, user.avatarUrl, user.createdAt)
          : undefined,
        blockedAt: b.blockedAt,
      }
    })
}

function toReportItem(entry: {
  id: number
  reporterUserId: number
  targetType: Report['targetType']
  targetId: string
  reason: string
  status: ReportStatus
  handlingNote?: string
  createdAt: string
  handledAt?: string
}): Report {
  return {
    reportId: String(entry.id),
    reporterUserId: String(entry.reporterUserId),
    targetType: entry.targetType,
    targetId: entry.targetId,
    reason: entry.reason,
    status: entry.status,
    handlingNote: entry.handlingNote,
    createdAt: entry.createdAt,
    handledAt: entry.handledAt,
  }
}

/** 提交举报 */
export function createReport(reporterId: number, body: ReportCreateRequest): Report {
  const db = getMockDb()
  const reason = body.reason?.trim()
  if (!reason) {
    throw new MockBusinessError(40007, 'Report is invalid')
  }
  if (body.targetType === 'user' && String(reporterId) === body.targetId) {
    throw new MockBusinessError(40007, 'Report is invalid')
  }

  if (body.targetType === 'user') {
    const targetId = Number(body.targetId)
    const user = db.users.find((u) => u.id === targetId)
    if (!user) {
      throw new MockBusinessError(40000, `User ${body.targetId} is not visible`)
    }
  }

  const now = new Date().toISOString()
  const id = nextId('reports')
  const entry = {
    id,
    reporterUserId: reporterId,
    targetType: body.targetType,
    targetId: body.targetId,
    reason,
    status: 'pending' as const,
    createdAt: now,
  }
  db.reports.push(entry)
  persistMockDb()
  return toReportItem(entry)
}

/** 查看我提交的举报 */
export function listMyReports(
  reporterId: number,
  status?: ReportStatus,
  page = 1,
  pageSize = 20,
): { items: Report[]; total: number; page: number; pageSize: number; totalPages: number } {
  const db = getMockDb()
  const filtered = db.reports
    .filter((r) => r.reporterUserId === reporterId)
    .filter((r) => (status ? r.status === status : true))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))

  const total = filtered.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const start = (page - 1) * pageSize
  const items = filtered.slice(start, start + pageSize).map(toReportItem)

  return { items, total, page, pageSize, totalPages }
}

/** 分页获取会话列表 */
export function listConversations(
  userId: number,
  page = 1,
  pageSize = 20,
): MockPageResult<ConversationSummary> {
  return paginate(getConversations(userId), page, pageSize)
}

/** 获取会话列表（自动为缺少会话的好友关系创建会话） */
export function getConversations(userId: number): ConversationSummary[] {
  const db = getMockDb()
  repairConversationStore()

  const friendIds = db.friends.filter((f) => f.userId === userId).map((f) => f.friendId)

  for (const friendId of friendIds) {
    ensureFriendConversation(db, userId, friendId)
  }

  persistMockDb()

  return db.conversations
    .filter((c) => {
      if (!c.participantIds.includes(userId)) return false
      if (c.kind !== 'friend') return true
      const otherId = c.participantIds.find((id) => id !== userId)
      return otherId !== undefined && areFriends(db, userId, otherId)
    })
    .sort((a, b) => new Date(b.lastMessageAt).getTime() - new Date(a.lastMessageAt).getTime())
    .map((c) => {
      let title = c.name
      if (c.kind === 'friend' && !title) {
        const other = c.participantIds.find((id) => id !== userId)
        const otherUser = db.users.find((u) => u.id === other)
        const remark = db.friends.find((f) => f.userId === userId && f.friendId === other)?.remark
        title = remark?.trim() || otherUser?.nickname || '未知'
      }
      let avatar: MediaFile | undefined
      if (c.kind === 'friend') {
        const otherId = c.participantIds.find((id) => id !== userId)
        const otherUser = db.users.find((u) => u.id === otherId)
        if (otherUser?.avatarUrl) {
          avatar = avatarMediaFile(otherUser.id, otherUser.avatarUrl, otherUser.createdAt)
        }
      } else {
        const team = db.teams.find((t) => t.conversationId === c.id)
        if (team) {
          title = `${team.name} (${team.memberCount})`
          if (team.coverUrl) {
            avatar = mediaFileFromId(
              `media_team_${team.id}`,
              team.createdAt,
              `team_${team.id}`,
              'teamAlbum',
            )
          }
        }
      }

      return {
        conversationId: String(c.id),
        kind: c.kind,
        title,
        avatar,
        lastMessagePreview: c.lastMessage || undefined,
        updatedAt: c.lastMessageAt,
        unreadCount: countUnreadMessages(db.messages, userId, c.id),
      }
    })
}

/** 获取会话消息列表 */
export function getMessages(
  conversationId: number,
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<ChatMessage> {
  const db = getMockDb()
  repairConversationStore()

  const conv = db.conversations.find((c) => c.id === conversationId)
  if (!conv) {
    throw new MockBusinessError(50004, '会话不存在')
  }
  if (!conv.participantIds.includes(userId)) {
    throw new MockBusinessError(50005, '你不是该会话的成员')
  }

  const convMessages = db.messages.filter((m) => m.conversationId === conversationId)
  const msgs = convMessages
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .map((m) => buildChatMessageDto(conv, m, userId, convMessages))
  return paginate(msgs, page, pageSize)
}

/** 搜索小队（发现小队，返回所有活跃小队） */
export function searchTeams(
  keyword?: string,
  tags?: string[],
  page = 1,
  pageSize = 20,
): MockPageResult<TeamProfile> {
  const db = getMockDb()
  let teams = db.teams.filter((t) => t.status === 'active')

  if (keyword) {
    const kw = keyword.toLowerCase()
    teams = teams.filter(
      (t) => t.name.toLowerCase().includes(kw) || t.description.toLowerCase().includes(kw),
    )
  }
  if (tags?.length) {
    const tagSet = tags.map((t) => t.toLowerCase())
    teams = teams.filter((t) => t.tags?.some((tag) => tagSet.includes(tag.toLowerCase())))
  }

  return paginate(
    teams.map((t) => teamToProfile(t)),
    page,
    pageSize,
  )
}

/** 获取当前用户加入的小队列表（含已解散/已停用，按加入时间倒序） */
export function listMyTeams(
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<TeamProfile> {
  const db = getMockDb()
  const memberships = db.teamMembers
    .filter((m) => m.userId === userId)
    .sort((a, b) => new Date(b.joinedAt).getTime() - new Date(a.joinedAt).getTime())

  const profiles = memberships
    .map((membership) => {
      const team = db.teams.find((t) => t.id === membership.teamId)
      return team ? teamToProfile(team) : null
    })
    .filter((team): team is TeamProfile => team !== null)

  return paginate(profiles, page, pageSize)
}

/** 获取用户加入的小队列表（测试兼容） */
export function getTeams(userId: number): TeamProfile[] {
  return listMyTeams(userId, 1, 200).items
}

/** 统计当前用户可审核的待处理入队申请数 */
export function countPendingJoinRequestsForManager(userId: number): number {
  const db = getMockDb()
  const managedTeamIds = db.teamMembers
    .filter((m) => m.userId === userId && (m.role === 'leader' || m.role === 'admin'))
    .map((m) => m.teamId)

  return db.teamJoinRequests.filter(
    (r) => managedTeamIds.includes(r.teamId) && r.status === 'pending',
  ).length
}

/** 获取小队详情 */
export function getTeamDetail(teamId: number): TeamProfile {
  const db = getMockDb()
  const team = db.teams.find((t) => t.id === teamId)
  if (!team) {
    throw new MockBusinessError(40001, '小队不存在')
  }

  return teamToProfile(team)
}

/** 队内活动列表 */
export function listTeamActivities(
  teamId: number,
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<ActivitySummary> {
  const db = getMockDb()
  const member = db.teamMembers.find((m) => m.teamId === teamId && m.userId === userId)
  if (!member) throw new MockBusinessError(40015, 'Team member does not exist')
  const items = db.activities
    .filter((a) => a.teamId === teamId)
    .map((a) => activityToFeedItem(db, a))
  return paginate(items, page, pageSize)
}

/** 发布队内活动 */
export function createTeamActivity(
  teamId: number,
  userId: number,
  data: ActivityUpsertRequest,
): ActivityDetail {
  const db = getMockDb()
  const team = db.teams.find((t) => t.id === teamId)
  const operator = db.teamMembers.find((m) => m.teamId === teamId && m.userId === userId)
  if (!operator || (operator.role !== 'leader' && operator.role !== 'admin')) {
    throw new MockBusinessError(40020, 'Team operation is not allowed')
  }
  if (!team || team.status !== 'active') {
    throw new MockBusinessError(40011, 'Team is unavailable')
  }

  const now = new Date().toISOString()
  const activityId = nextId('activities')
  const loc = data.location.point

  db.activities.push({
    id: activityId,
    creatorId: userId,
    teamId,
    title: data.title,
    introduction: data.introduction,
    safetyNotice: data.safetyNotice ?? '请注意活动安全，遵守场地规则。',
    coverUrl: `https://picsum.photos/seed/teamact${activityId}/400/225`,
    images: [],
    startTime: data.startAt,
    endTime: data.endAt,
    registrationDeadline: data.registrationDeadline,
    location: {
      longitude: loc.longitude,
      latitude: loc.latitude,
      city: data.location.city,
      address: data.location.address,
      placeName: data.location.placeName ?? data.location.address,
    },
    fee: data.feeAmount ?? 0,
    capacity: data.capacity,
    registeredCount: 0,
    minAge: data.minAge ?? 0,
    tags: data.tags ?? ['队内活动'],
    runtimeStatus: 'registering',
    reviewStatus: 'approved',
    isTakenDown: false,
    requireLocationCheck: data.requireLocationCheck,
    createdAt: now,
  })
  persistMockDb()
  return getActivityDetail(activityId)
}

/** 获取队内活动详情 */
export function getTeamActivity(
  teamId: number,
  activityId: number,
  userId: number,
): ActivityDetail {
  const db = getMockDb()
  const member = db.teamMembers.find((m) => m.teamId === teamId && m.userId === userId)
  if (!member) throw new MockBusinessError(40015, 'Team member does not exist')
  const activity = db.activities.find((a) => a.id === activityId && a.teamId === teamId)
  if (!activity) throw new MockBusinessError(40018, 'Team activity is not visible')
  return getActivityDetail(activityId)
}

/** 获取兴趣标签列表 */
export function getInterestTags(): InterestTagItem[] {
  const db = getMockDb()
  return db.interestTags.map((t) => ({ name: t.name }))
}

/** 获取模板列表
 *
 * 前置条件：page 与 pageSize 为正整数。
 * 后置条件：返回符合 OpenAPI PageResult<ActivityTemplate> 的分页模板列表。
 * 不变量：模板字段与 TypeSpec ActivityTemplate 保持一致。
 */
export function getTemplates(page: number, pageSize: number): MockPageResult<ActivityTemplate> {
  const db = getMockDb()
  const templates: ActivityTemplate[] = db.templates.map((t) => ({
    templateId: String(t.id),
    name: t.name,
    activityType: t.activityType,
    defaultTags: t.tags,
    defaultIntroduction: t.defaultIntroduction,
    defaultSafetyNotice: t.defaultSafetyNotice,
    defaultCapacity: t.defaultCapacity,
    defaultCoverImage: mediaFileFromId(
      `media_tpl_${t.id}`,
      new Date().toISOString(),
      `tpl_${t.id}`,
    ),
  }))
  return paginate(templates, page, pageSize)
}

/** 获取商家资料 */
export function getMerchantProfile(userId: number): MerchantProfile {
  const db = getMockDb()
  const user = db.users.find((u) => u.id === userId && u.kind === 'merchant')
  if (!user) {
    throw new MockBusinessError(10003, '商家用户不存在')
  }
  const qualificationStatus = user.qualificationStatus ?? 'not_submitted'
  return {
    userId: String(user.id),
    nickname: user.nickname,
    merchantName: user.merchantName ?? user.nickname,
    accountStatus: user.accountStatus,
    qualificationStatus,
    interestedActivityFields: user.interestedActivityFields ?? ['运动', '户外', '社交'],
    avatar: user.avatarUrl ? avatarMediaFile(user.id, user.avatarUrl, user.createdAt) : undefined,
    qualification: {
      status: qualificationStatus,
      submittedAt: user.qualificationSubmittedAt,
      reviewedAt: user.qualificationReviewedAt,
      rejectReason: user.qualificationRejectReason,
      licenseImageUrls: user.qualificationLicenseMediaIds?.map(
        (mediaId) => `https://picsum.photos/seed/${encodeURIComponent(mediaId)}/640/480`,
      ),
    },
  }
}

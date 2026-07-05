/** Mock 数据库实体类型定义 */

export interface MockUser {
  id: number
  email: string
  password: string
  nickname: string
  avatarUrl: string
  kind: 'personal' | 'merchant'
  accountStatus: 'active' | 'inactive' | 'banned'
  gender: 'male' | 'female' | 'unknown'
  birthday: string
  signature: string
  interestTagIds: number[]
  createdAt: string
  merchantName?: string
  interestedActivityFields?: string[]
  qualificationStatus?: 'not_submitted' | 'pending' | 'approved' | 'rejected'
  qualificationLicenseMediaIds?: string[]
  qualificationSubmittedAt?: string
  qualificationReviewedAt?: string
  qualificationRejectReason?: string
}

export interface MockActivity {
  id: number
  creatorId: number
  title: string
  introduction: string
  safetyNotice: string
  coverUrl: string
  images: string[]
  imageIds?: string[]
  startTime: string
  endTime: string
  registrationDeadline: string
  location: {
    longitude: number
    latitude: number
    city: string
    address: string
    placeName: string
  }
  fee: number
  feeDescription?: string
  capacity: number
  registeredCount: number
  minAge: number
  tags: string[]
  runtimeStatus:
    'notStarted' | 'registering' | 'registrationClosed' | 'ongoing' | 'ended' | 'takenDown'
  reviewStatus: 'draft' | 'pending' | 'approved' | 'rejected' | 'changeRequired'
  isTakenDown: boolean
  createdAt: string
  aiContentReview?: {
    status: 'succeeded'
    riskLevel: 'low' | 'medium' | 'high' | 'uncertain'
    suggestedReviewStatus: 'pending' | 'approved' | 'rejected' | 'changeRequired'
    reasons: string[]
  }
  /** 审核记录列表，由 submitActivity 或管理员操作设置 */
  reviewRecords?: Array<{
    reviewId: string
    result: 'pending' | 'approved' | 'rejected' | 'changeRequired'
    reason?: string
    reviewedAt: string
    reviewerId?: string
  }>
  /** 是否要求签到时校验位置；未设置时 mock 默认视为 false */
  requireLocationCheck?: boolean
}

export interface MockDraft {
  id: number
  creatorId: number
  title: string
  introduction: string
  safetyNotice: string
  coverUrl: string
  images: string[]
  imageIds?: string[]
  startTime: string
  endTime: string
  registrationDeadline: string
  location: {
    longitude: number
    latitude: number
    city: string
    address: string
    placeName: string
  }
  fee: number
  feeDescription?: string
  capacity: number
  minAge: number
  tags: string[]
  /** 是否要求签到时校验位置；未设置时 mock 默认视为 false */
  requireLocationCheck?: boolean
  reviewStatus: 'draft' | 'rejected' | 'changeRequired'
  sourceType: 'manual' | 'template' | 'clone' | 'ai'
  createdAt: string
  updatedAt: string
}

export interface MockRegistration {
  id: number
  activityId: number
  userId: number
  status: 'registered' | 'canceled' | 'checkedIn' | 'waiting' | 'waitingConfirmation'
  registeredAt: string
}

export interface MockWaitlistEntry {
  id: number
  activityId: number
  userId: number
  position: number
  status: 'waiting' | 'waitingConfirmation' | 'confirmed' | 'expired'
  joinedAt: string
}

export interface MockCheckIn {
  id: number
  activityId: number
  userId: number
  checkedInAt: string
}

export interface MockReview {
  id: number
  activityId: number
  userId: number
  rating: number
  content: string
  tags: string[]
  createdAt: string
}

export interface MockSummary {
  id: number
  activityId: number
  userId: number
  title: string
  content: string
  images: string[]
  imageTags: Array<{ mediaId: string; tags: string[] }>
  createdAt: string
}

export interface MockFriend {
  userId: number
  friendId: number
  remark: string
  source: string
  createdAt: string
}

export interface MockFriendRequest {
  id: number
  fromUserId: number
  toUserId: number
  status: 'pending' | 'accepted' | 'rejected'
  source: string
  message: string
  createdAt: string
}

export interface MockFollow {
  followerId: number
  followingId: number
  createdAt: string
}

export interface MockConversation {
  id: number
  kind: 'friend' | 'team'
  name: string
  participantIds: number[]
  lastMessage: string
  lastMessageAt: string
}

export interface MockMessage {
  id: number
  conversationId: number
  senderId: number
  kind: 'text' | 'image' | 'location'
  content: string
  status: 'sent' | 'recalled'
  createdAt: string
}

export interface MockTeam {
  id: number
  name: string
  description: string
  coverUrl: string
  leaderId: number
  joinMode: 'publicJoin' | 'approvalRequired'
  status: 'active' | 'dissolved' | 'disabled'
  maxMembers: number
  memberCount: number
  tags?: string[]
  createdAt: string
}

export interface MockTeamMember {
  id: number
  teamId: number
  userId: number
  role: 'leader' | 'admin' | 'member'
  joinedAt: string
}

export interface MockTeamJoinRequest {
  id: number
  teamId: number
  userId: number
  status: 'pending' | 'accepted' | 'rejected'
  message: string
  createdAt: string
}

export interface MockInterestTag {
  id: number
  name: string
  category: string
}

export interface MockTemplate {
  id: number
  name: string
  /** 模板所属活动类型（如 运动、桌游、户外、学习、公益），对应 OpenAPI activityType */
  activityType: string
  coverUrl: string
  tags: string[]
  defaultTitle: string
  defaultIntroduction: string
  /** 模板默认安全须知，对应 OpenAPI defaultSafetyNotice */
  defaultSafetyNotice: string
  /** 模板默认人数上限，对应 OpenAPI defaultCapacity */
  defaultCapacity: number
}

/** Mock API 响应格式 */
export interface MockApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页结果 */
export interface MockPageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

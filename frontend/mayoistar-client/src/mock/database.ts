/**
 * 内存态 Mock 数据库
 *
 * 所有 mock API 读写此数据库。
 * H5 模式下通过 uni storage 持久化，应用重启后自动恢复。
 * 提供 resetMockDb() 重置到种子数据。
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
  MockReport,
  MockConversation,
  MockMessage,
  MockTeam,
  MockTeamMember,
  MockTeamJoinRequest,
  MockTeamAnnouncement,
  MockTeamPoll,
  MockTeamMedia,
  MockInterestTag,
  MockTemplate,
} from './types'
import { createSeedData } from './seed'

const STORAGE_KEY = 'mayoistar_mock_db'

export interface MockDatabase {
  users: MockUser[]
  activities: MockActivity[]
  drafts: MockDraft[]
  registrations: MockRegistration[]
  waitlist: MockWaitlistEntry[]
  checkins: MockCheckIn[]
  reviews: MockReview[]
  summaries: MockSummary[]
  friends: MockFriend[]
  friendRequests: MockFriendRequest[]
  follows: MockFollow[]
  blacklist: MockBlacklist[]
  reports: MockReport[]
  conversations: MockConversation[]
  messages: MockMessage[]
  teams: MockTeam[]
  teamMembers: MockTeamMember[]
  teamJoinRequests: MockTeamJoinRequest[]
  teamAnnouncements: MockTeamAnnouncement[]
  teamPolls: MockTeamPoll[]
  teamMedia: MockTeamMedia[]
  interestTags: MockInterestTag[]
  templates: MockTemplate[]
  /** 各实体的自增 ID 生成器 */
  nextId: Record<string, number>
}

let db: MockDatabase

/**
 * 初始化数据库（从 storage 恢复或使用种子数据）
 *
 * 前置条件：uni 全局对象可用
 * 后置条件：db 变量被赋值为有效的 MockDatabase 实例
 */
export function initMockDb(): MockDatabase {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (raw) {
      db = JSON.parse(raw as string) as MockDatabase
      // 基本完整性校验：确保关键数组存在
      if (db.users && db.activities && db.nextId) {
        repairMockDbShape(db)
        repairConversationStore()
        persistMockDb()
        return db
      }
    }
  } catch {
    /* storage 不可用或数据损坏，使用种子数据 */
  }

  db = createSeedData()
  persistMockDb()
  return db
}

/**
 * 获取数据库引用
 *
 * 前置条件：initMockDb 已被调用过
 */
export function getMockDb(): MockDatabase {
  if (!db) {
    return initMockDb()
  }
  return db
}

/**
 * 持久化到 storage
 *
 * 后置条件：db 内容被序列化写入 uni storage
 */
export function persistMockDb(): void {
  try {
    uni.setStorageSync(STORAGE_KEY, JSON.stringify(db))
  } catch {
    /* storage 不可用，静默失败 */
  }
}

/**
 * 重置到种子数据
 *
 * 后置条件：db 被替换为全新的种子数据并持久化
 */
export function resetMockDb(): void {
  db = createSeedData()
  persistMockDb()
}

/**
 * 生成下一个 ID
 *
 * 前置条件：entity 名称存在于 nextId 记录中
 * 后置条件：nextId[entity] 自增 1，返回自增前的值
 *
 * @param entity 实体名称，如 'users'、'activities'
 */
export function nextId(entity: string): number {
  if (!db.nextId[entity]) {
    db.nextId[entity] = 1
  }
  const id = db.nextId[entity]
  db.nextId[entity] = id + 1
  return id
}

/** 补齐持久化数据中可能缺失的数组字段，避免 workflow 运行时异常 */
export function repairMockDbShape(database: MockDatabase): void {
  const arrayKeys: (keyof MockDatabase)[] = [
    'users',
    'activities',
    'drafts',
    'registrations',
    'waitlist',
    'checkins',
    'reviews',
    'summaries',
    'friends',
    'friendRequests',
    'follows',
    'blacklist',
    'reports',
    'conversations',
    'messages',
    'teams',
    'teamMembers',
    'teamJoinRequests',
    'teamAnnouncements',
    'teamPolls',
    'teamMedia',
    'interestTags',
    'templates',
  ]
  for (const key of arrayKeys) {
    if (!Array.isArray(database[key])) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ;(database as any)[key] = []
    }
  }
  if (!database.nextId || typeof database.nextId !== 'object') {
    database.nextId = {}
  }
  repairAcceptedFriendships(database)
}

/** 将已接受但未落库的好友申请同步为双向好友关系 */
function repairAcceptedFriendships(database: MockDatabase): void {
  for (const req of database.friendRequests) {
    if (req.status !== 'accepted') continue
    const { fromUserId: a, toUserId: b, source, createdAt } = req
    const hasAB = database.friends.some((f) => f.userId === a && f.friendId === b)
    const hasBA = database.friends.some((f) => f.userId === b && f.friendId === a)
    if (!hasAB) {
      database.friends.push({
        userId: a,
        friendId: b,
        remark: '',
        groupTags: [],
        source,
        createdAt,
      })
    }
    if (!hasBA) {
      database.friends.push({
        userId: b,
        friendId: a,
        remark: '',
        groupTags: [],
        source,
        createdAt,
      })
    }
  }
}

/** 好友会话参与者对唯一键 */
function friendPairKey(userA: number, userB: number): string {
  return userA < userB ? `${userA}:${userB}` : `${userB}:${userA}`
}

/**
 * 修复会话存储：同步 nextId、消除重复 ID、合并重复好友会话
 *
 * 根因：历史种子 nextId.conversations 与已有会话 ID 不一致，会生成重复会话并污染消息列表。
 */
export function repairConversationStore(): void {
  if (!db?.conversations || !db.messages || !db.nextId) return

  const maxConvId = db.conversations.reduce((max, c) => Math.max(max, c.id), 0)
  const maxMsgId = db.messages.reduce((max, m) => Math.max(max, m.id), 0)
  if (!db.nextId.conversations || db.nextId.conversations <= maxConvId) {
    db.nextId.conversations = maxConvId + 1
  }
  if (!db.nextId.messages || db.nextId.messages <= maxMsgId) {
    db.nextId.messages = maxMsgId + 1
  }

  // 相同 numeric id 的会话：保留先出现的记录，后者换新 id
  const seenConvIds = new Set<number>()
  for (const conv of db.conversations) {
    if (!seenConvIds.has(conv.id)) {
      seenConvIds.add(conv.id)
      continue
    }

    const newId = nextId('conversations')
    if (conv.kind === 'friend' && conv.participantIds.length === 2) {
      const participants = new Set(conv.participantIds)
      db.messages.forEach((msg) => {
        if (msg.conversationId === conv.id && participants.has(msg.senderId)) {
          msg.conversationId = newId
        }
      })
    }
    conv.id = newId
    seenConvIds.add(newId)
  }

  // 相同好友对的重复会话：保留消息更多的那条
  const pairKeep = new Map<string, MockConversation>()
  const removeConvIds = new Set<number>()

  for (const conv of db.conversations) {
    if (conv.kind !== 'friend' || conv.participantIds.length !== 2) continue

    const key = friendPairKey(conv.participantIds[0], conv.participantIds[1])
    const kept = pairKeep.get(key)
    if (!kept) {
      pairKeep.set(key, conv)
      continue
    }

    const keptCount = db.messages.filter((m) => m.conversationId === kept.id).length
    const currentCount = db.messages.filter((m) => m.conversationId === conv.id).length
    const remove = currentCount > keptCount ? kept : conv
    const keep = remove === kept ? conv : kept

    db.messages.forEach((msg) => {
      if (msg.conversationId === remove.id) {
        msg.conversationId = keep.id
      }
    })
    removeConvIds.add(remove.id)
    pairKeep.set(key, keep)
  }

  if (removeConvIds.size > 0) {
    db.conversations = db.conversations.filter((c) => !removeConvIds.has(c.id))
  }
}

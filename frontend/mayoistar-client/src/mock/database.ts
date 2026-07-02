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
  MockConversation,
  MockMessage,
  MockTeam,
  MockTeamMember,
  MockTeamJoinRequest,
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
  conversations: MockConversation[]
  messages: MockMessage[]
  teams: MockTeam[]
  teamMembers: MockTeamMember[]
  teamJoinRequests: MockTeamJoinRequest[]
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

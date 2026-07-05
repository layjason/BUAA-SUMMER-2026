import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetMockDb } from '@/mock/database'
import {
  getActivityDetail,
  getFeed,
  getParticipationState,
  registerForActivity,
} from '@/mock/workflow'

vi.stubGlobal('uni', {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
})

describe('mock schema-types 契约应用', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetMockDb()
  })

  it('workflow.ts 应引用 schema-types 且不再用 Record<string, unknown> 作返回值', () => {
    const workflowPath = join(process.cwd(), 'src/mock/workflow.ts')
    const content = readFileSync(workflowPath, 'utf8')

    expect(content).toContain("from './schema-types'")
    expect(content).not.toMatch(/Record<string,\s*unknown>/)
  })

  it('workflow.ts 关键请求参数应使用 schema-types 类型', () => {
    const workflowPath = join(process.cwd(), 'src/mock/workflow.ts')
    const content = readFileSync(workflowPath, 'utf8')

    const signatureChecks: Array<[RegExp, string]> = [
      [/export function register\([\s\S]*?kind: UserKind/, 'register kind'],
      [/export function getFeed\([\s\S]*?tab: ActivityFeedTab/, 'getFeed tab'],
      [
        /export function registerForActivity\([\s\S]*?request: RegisterActivityRequest/,
        'registerForActivity request',
      ],
      [/export function checkIn\([\s\S]*?request: CheckInRequest/, 'checkIn request'],
      [
        /export function sendFriendRequest\([\s\S]*?request: FriendRequestCreate/,
        'sendFriendRequest request',
      ],
      [/export function sendMessage\([\s\S]*?request: SendMessageRequest/, 'sendMessage request'],
      [/export function createTeam\([\s\S]*?data: TeamCreateRequest/, 'createTeam data'],
      [/export function joinTeam\([\s\S]*?request: JoinTeamRequestBody/, 'joinTeam request'],
      [
        /export function searchActivities\([\s\S]*?filters: ActivitySearchQuery/,
        'searchActivities filters',
      ],
      [/export function createDraft\([\s\S]*?data: MockDraftUpsertInput/, 'createDraft data'],
      [/export function updateDraft\([\s\S]*?data: MockDraftUpsertInput/, 'updateDraft data'],
      [
        /export function createReview\([\s\S]*?request: ActivityReviewRequest/,
        'createReview request',
      ],
      [
        /export function createSummary\([\s\S]*?request: ActivitySummaryPostRequest/,
        'createSummary request',
      ],
    ]

    for (const [pattern, label] of signatureChecks) {
      expect(content, label).toMatch(pattern)
    }
  })

  it('活动信息流项应包含 ActivitySummary 必填字段', () => {
    const result = getFeed('latest', 1, 10)

    expect(result.items.length).toBeGreaterThan(0)
    const first = result.items[0]
    expect(first).toMatchObject({
      activityId: expect.any(String),
      title: expect.any(String),
      startAt: expect.any(String),
      endAt: expect.any(String),
      capacity: expect.any(Number),
      registeredCount: expect.any(Number),
      occupiedCount: expect.any(Number),
      reviewStatus: expect.any(String),
      runtimeStatus: expect.any(String),
      tags: expect.any(Array),
      location: expect.objectContaining({
        city: expect.any(String),
        address: expect.any(String),
        point: expect.objectContaining({
          longitude: expect.any(Number),
          latitude: expect.any(Number),
        }),
      }),
      requireLocationCheck: expect.any(Boolean),
    })
    expect(first.coverImage?.signedUrl).toContain('https://')
  })

  it('活动详情应包含 ActivityDetail 关键字段', () => {
    const detail = getActivityDetail(1)

    expect(detail).toMatchObject({
      activityId: '1',
      organizerId: expect.any(String),
      organizerName: expect.any(String),
      images: expect.any(Array),
      reviewRecords: expect.any(Array),
      occupiedCount: expect.any(Number),
      registeredCount: expect.any(Number),
      waitingCount: expect.any(Number),
    })
    expect(detail.coverImage?.signedUrl).toContain('https://')
    expect(detail.images[0]?.signedUrl).toContain('https://')
  })

  it('参与状态应符合 ActivityParticipationState 且无 mock 扩展字段', () => {
    const state = getParticipationState(2, 10001)

    expect(state).toMatchObject({
      canRegister: expect.any(Boolean),
      canCancelRegistration: expect.any(Boolean),
      canCheckIn: expect.any(Boolean),
      canConfirmWaitingSeat: expect.any(Boolean),
      canReview: expect.any(Boolean),
    })
    expect(state).not.toHaveProperty('isOrganizer')
  })

  it('报名结果应符合 RegistrationResult', () => {
    const result = registerForActivity(3, 10005, { acceptedSafetyNotice: true })

    expect(result).toMatchObject({
      activityId: '3',
      registrationId: expect.any(String),
      status: expect.any(String),
    })
  })
})

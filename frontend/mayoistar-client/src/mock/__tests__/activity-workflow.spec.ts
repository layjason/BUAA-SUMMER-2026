import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetMockDb } from '@/mock/database'
import {
  cancelRegistration,
  checkIn,
  createDraft,
  getCheckIns,
  getParticipationState,
  submitActivity,
} from '@/mock/workflow'

vi.stubGlobal('uni', {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
})

describe('活动 mock workflow 契约对齐', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetMockDb()
  })

  it('保存草稿应使用 OpenAPI 字段并在提交后返回完整活动详情', () => {
    const draft = createDraft(10001, {
      title: '危险攀岩体验',
      tags: ['攀岩', '户外'],
      introduction: '危险项目需要人工确认，但仍应使用 OpenAPI 枚举表达审核状态。',
      safetyNotice: '请听从教练安排。',
      startAt: '2026-08-01T09:00:00+08:00',
      endAt: '2026-08-01T12:00:00+08:00',
      registrationDeadline: '2026-07-30T20:00:00+08:00',
      location: {
        city: '北京',
        address: '朝阳区攀岩馆',
        placeName: '城市岩壁',
        point: { longitude: 116.45, latitude: 39.93 },
      },
      capacity: 20,
      feeAmount: 88,
      feeDescription: '现场支付',
      minAge: 16,
      imageIds: ['media-1', 'media-2'],
    })

    expect(draft).toMatchObject({
      title: '危险攀岩体验',
      startAt: '2026-08-01T09:00:00+08:00',
      endAt: '2026-08-01T12:00:00+08:00',
      feeAmount: 88,
      feeDescription: '现场支付',
      location: {
        city: '北京',
        address: '朝阳区攀岩馆',
        placeName: '城市岩壁',
        point: { longitude: 116.45, latitude: 39.93 },
      },
    })
    expect(draft.images).toHaveLength(2)

    const submitted = submitActivity(Number(draft.activityId))

    expect(submitted).toMatchObject({
      activityId: expect.any(String),
      title: '危险攀岩体验',
      startAt: '2026-08-01T09:00:00+08:00',
      endAt: '2026-08-01T12:00:00+08:00',
      feeAmount: 88,
      reviewStatus: 'pending',
      runtimeStatus: 'registering',
      organizerId: '10001',
      images: expect.any(Array),
      waitingCount: 0,
      reviewRecords: expect.any(Array),
    })
    expect(submitted.reviewStatus).not.toBe('riskReview')
  })

  it('报名中未来活动已报名用户应可取消但不可签到', () => {
    const state = getParticipationState(2, 10001)

    expect(state).toMatchObject({
      status: 'registered',
      canCancelRegistration: true,
      canCheckIn: false,
    })
  })

  it('取消报名应返回 RegistrationResult 并触发候补待确认', () => {
    const result = cancelRegistration(5, 10001)

    expect(result).toMatchObject({
      activityId: '5',
      registrationId: '16',
      status: 'canceled',
    })

    const waitingState = getParticipationState(5, 10008)
    expect(waitingState).toMatchObject({
      status: 'waitingConfirmation',
      canConfirmWaitingSeat: true,
      waitingRank: 1,
    })
  })

  it('扫码签到应返回 OpenAPI CheckInRecord 必填字段', () => {
    const result = checkIn(6, 10001, { qrCodeToken: 'qr_test_token' })

    expect(result).toMatchObject({
      registrationId: '24',
      userId: '10001',
      nickname: expect.any(String),
      registrationStatus: 'checkedIn',
      checkedInAt: expect.any(String),
    })
    expect(result).not.toHaveProperty('activityId')
  })

  it('签到列表应返回 OpenAPI 分页响应', () => {
    const result = getCheckIns(6, 1, 20)

    expect(result).toMatchObject({
      page: 1,
      pageSize: 20,
      totalPages: 1,
    })
    expect(result.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          registrationId: '22',
          registrationStatus: 'checkedIn',
          checkedInAt: expect.any(String),
        }),
      ]),
    )
  })
})

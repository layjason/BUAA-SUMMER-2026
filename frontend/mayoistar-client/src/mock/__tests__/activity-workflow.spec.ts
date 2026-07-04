import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetMockDb } from '@/mock/database'
import {
  cancelRegistration,
  checkIn,
  createDraft,
  createReview,
  createSummary,
  getCheckIns,
  getMapActivities,
  getMyActivities,
  getMyActivityReview,
  getMyRegistrations,
  getParticipationState,
  listActivityReviews,
  listActivitySummaries,
  registerForActivity,
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

  it('我的报名列表应包含候补队列记录', () => {
    registerForActivity(1, 10001, { acceptedSafetyNotice: true })

    const result = getMyRegistrations(10001, 1, 100)
    const waiting = result.items.find((item) => item.activityId === '1')

    expect(waiting).toMatchObject({
      registrationStatus: 'waiting',
      waitingRank: expect.any(Number),
      requireLocationCheck: false,
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

  it('开启位置校验的活动签到必须提供现场附近坐标', () => {
    const draft = createDraft(10001, {
      title: '带位置校验的晨跑',
      tags: ['跑步', '运动'],
      introduction: '用于测试签到位置校验。',
      safetyNotice: '请按时到达集合点。',
      startAt: '2026-08-02T07:00:00+08:00',
      endAt: '2026-08-02T08:00:00+08:00',
      registrationDeadline: '2026-08-01T20:00:00+08:00',
      location: {
        city: '北京',
        address: '朝阳区奥林匹克森林公园南门',
        placeName: '奥林匹克森林公园',
        point: { longitude: 116.397, latitude: 40.02 },
      },
      capacity: 10,
      requireLocationCheck: true,
    })
    const activity = submitActivity(Number(draft.activityId))
    registerForActivity(Number(activity.activityId), 10001, { acceptedSafetyNotice: true })

    expect(() =>
      checkIn(Number(activity.activityId), 10001, { qrCodeToken: 'qr_test_token' }),
    ).toThrow('活动要求位置校验')
    expect(() =>
      checkIn(Number(activity.activityId), 10001, {
        qrCodeToken: 'qr_test_token',
        currentLocation: { longitude: 121.47, latitude: 31.23 },
      }),
    ).toThrow('当前位置距离活动地点过远')

    const result = checkIn(Number(activity.activityId), 10001, {
      qrCodeToken: 'qr_test_token',
      currentLocation: { longitude: 116.397, latitude: 40.02 },
    })
    expect(result.registrationStatus).toBe('checkedIn')
  })

  it('已结束且已签到用户应可提交活动评价', () => {
    expect(getMyActivityReview(7, 10001)).toEqual({})

    const state = getParticipationState(7, 10001)
    expect(state.status).toBe('checkedIn')

    const review = createReview(7, 10001, {
      rating: 5,
      content: '活动很棒，期待下次再参加。',
      tags: ['组织有序', '氛围很好'],
    })

    expect(review).toMatchObject({
      activityId: '7',
      userId: '10001',
      rating: 5,
      tags: ['组织有序', '氛围很好'],
      reviewId: expect.any(String),
      createdAt: expect.any(String),
    })
    expect(getMyActivityReview(7, 10001).review).toMatchObject({
      activityId: '7',
      userId: '10001',
    })
  })

  it('活动详情应能查询总结与评价列表', () => {
    const summaries = listActivitySummaries(7, 1, 10)
    const reviews = listActivityReviews(7, 1, 10)

    expect(summaries.items).toHaveLength(1)
    expect(summaries.items[0]).toMatchObject({
      activityId: '7',
      title: expect.any(String),
    })
    expect(reviews.items.length).toBeGreaterThanOrEqual(1)
    expect(reviews.items[0]).toMatchObject({
      activityId: '7',
      nickname: expect.any(String),
      rating: expect.any(Number),
    })
  })

  it('每个活动仅允许发布一篇总结', () => {
    expect(() =>
      createSummary(7, 10001, {
        title: '重复总结',
        content: '不应成功',
        imageIds: [],
        confirmedImageTags: [],
      }),
    ).toThrow()
  })

  it('我创建的活动列表应包含审核状态字段', () => {
    const result = getMyActivities(10001, 1, 50)
    const pending = result.items.find((item) => item.activityId === '11')

    expect(pending).toMatchObject({
      title: '大型户外音乐节',
      reviewStatus: 'pending',
      runtimeStatus: 'registering',
    })
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

  it('地图点位查询应复用搜索筛选条件', () => {
    const results = getMapActivities(116.4, 39.9, 20000, {
      keyword: '夜跑',
      activityTypes: ['跑步'],
      city: '北京',
    })

    expect(results).toEqual([
      expect.objectContaining({
        activityId: '6',
        title: expect.stringContaining('夜跑'),
        point: { longitude: 116.397, latitude: 40.02 },
      }),
    ])
  })
})

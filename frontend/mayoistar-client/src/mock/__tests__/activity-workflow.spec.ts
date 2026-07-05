import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetMockDb } from '@/mock/database'
import { handleMockRequest } from '@/mock/mockServer'
import {
  cancelRegistration,
  checkIn,
  createDraft,
  createDraftFromTemplate,
  createReview,
  createSummary,
  getCheckIns,
  getMyActivities,
  getMyActivityReview,
  getParticipationState,
  getTemplates,
  getMerchantProfile,
  listActivityReviews,
  listActivitySummaries,
  submitMerchantQualification,
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

  it('从模板创建草稿应继承模板默认内容', () => {
    const templatePage = getTemplates(1, 100)
    const templates = templatePage.items
    const firstTemplate = templates[0]

    expect(templatePage).toMatchObject({
      total: 6,
      page: 1,
      pageSize: 100,
      totalPages: 1,
    })

    expect(templates.map((template) => template.name)).toEqual([
      '运动健身',
      '桌游聚会',
      '户外徒步',
      '学习交流',
      '公益活动',
      '城市探索',
    ])

    expect(firstTemplate).toMatchObject({
      templateId: expect.any(String),
      name: expect.any(String),
      activityType: expect.any(String),
      defaultTags: expect.arrayContaining([expect.any(String)]),
      defaultIntroduction: expect.any(String),
      defaultSafetyNotice: expect.any(String),
      defaultCapacity: expect.any(Number),
      defaultCoverImage: expect.objectContaining({
        signedUrl: expect.stringContaining('https://'),
      }),
    })

    const draft = createDraftFromTemplate(Number(firstTemplate.templateId), 10001)

    expect(draft).toMatchObject({
      title: expect.any(String),
      introduction: firstTemplate.defaultIntroduction,
      safetyNotice: firstTemplate.defaultSafetyNotice,
      capacity: firstTemplate.defaultCapacity,
      tags: firstTemplate.defaultTags,
      images: [expect.objectContaining({ signedUrl: firstTemplate.defaultCoverImage.signedUrl })],
    })
    expect(draft.title).not.toBe('')
    expect(draft.safetyNotice).not.toBe('')
    expect(draft.capacity).toBeGreaterThan(0)
  })

  it('模板 mock 路由应返回分页结构供页面读取 items', async () => {
    const response = await handleMockRequest('GET', '/activities/templates?page=1&pageSize=10')

    expect(response).not.toBeNull()
    expect(response?.data).toMatchObject({
      total: 6,
      page: 1,
      pageSize: 10,
      totalPages: 1,
      items: expect.arrayContaining([
        expect.objectContaining({ name: '运动健身' }),
        expect.objectContaining({ name: '桌游聚会' }),
        expect.objectContaining({ name: '户外徒步' }),
        expect.objectContaining({ name: '学习交流' }),
        expect.objectContaining({ name: '公益活动' }),
        expect.objectContaining({ name: '城市探索' }),
      ]),
    })
  })

  it('报名中未来活动已报名用户应可取消但不可签到', () => {
    const state = getParticipationState(2, 10001)

    expect(state).toMatchObject({
      status: 'registered',
      canCancelRegistration: true,
      canCheckIn: false,
      canReview: false,
    })
  })

  it('已结束且已签到未评价用户应可在评价窗口内评价', () => {
    const state = getParticipationState(7, 10001)

    expect(state).toMatchObject({
      status: 'checkedIn',
      canReview: true,
      reviewWindowEndsAt: expect.any(String),
    })
  })

  it('已结束未总结的演示活动应允许发起人发布总结且参与者评价', () => {
    const participantState = getParticipationState(13, 10001)

    expect(participantState).toMatchObject({
      status: 'checkedIn',
      canReview: true,
      reviewWindowEndsAt: expect.any(String),
    })

    expect(listActivitySummaries(13, 1, 10).items).toHaveLength(0)

    const summary = createSummary(13, 10002, {
      title: '社区花园共创日精彩回顾',
      content: '大家一起整理花箱、清理步道，留下了很棒的过程记录。',
      imageIds: ['media_summary_demo_1'],
      confirmedImageTags: [{ mediaId: 'media_summary_demo_1', tags: ['过程记录', '成果展示'] }],
    })

    expect(summary).toMatchObject({
      activityId: '13',
      title: '社区花园共创日精彩回顾',
      imageTags: [{ mediaId: 'media_summary_demo_1', tags: ['过程记录', '成果展示'] }],
    })
    expect(summary.images[0]).toMatchObject({
      mediaId: 'media_summary_demo_1',
      signedUrl: expect.stringContaining('media_summary_demo_1'),
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

  it('商家资质提交后应进入审核中状态', () => {
    const before = getMerchantProfile(10002)
    expect(before.qualificationStatus).toBe('not_submitted')

    const result = submitMerchantQualification(10002, {
      licenseMediaIds: ['media_license_1'],
    })
    expect(result).toEqual({})

    const after = getMerchantProfile(10002)
    expect(after.qualificationStatus).toBe('pending')
    expect(after.qualification).toMatchObject({
      status: 'pending',
      licenseImageUrls: expect.arrayContaining([expect.stringContaining('media_license_1')]),
    })
  })

  it('商家营业凭证上传应使用专用媒体用途', async () => {
    const response = await handleMockRequest('POST', '/identity/media/license', {
      filePath: '/tmp/license.jpg',
    })

    expect(response).toMatchObject({
      code: 200,
      data: {
        usage: 'merchantLicense',
        signedUrl: expect.stringContaining('https://picsum.photos/seed/'),
      },
    })
  })
})

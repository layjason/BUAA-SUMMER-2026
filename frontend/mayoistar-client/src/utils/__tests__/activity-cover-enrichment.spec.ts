import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ActivitySummary } from '@/api/types/activity-schemas'
import { mockMediaFile } from '@/test-utils/mock-media-file'
import {
  clearActivityCoverCache,
  enrichActivitiesWithCoverImages,
  resolveCoverFromDetail,
} from '@/utils/activity-cover-enrichment'

vi.mock('@/api/modules/activities', () => ({
  getActivityDetail: vi.fn(),
}))

import { getActivityDetail } from '@/api/modules/activities'

describe('activity-cover-enrichment', () => {
  beforeEach(() => {
    clearActivityCoverCache()
    vi.mocked(getActivityDetail).mockReset()
  })

  it('resolveCoverFromDetail 应优先使用 coverImage', () => {
    const cover = mockMediaFile({ mediaId: '1', signedUrl: '/media/cover.jpg' })
    expect(
      resolveCoverFromDetail({
        coverImage: cover,
        images: [mockMediaFile({ mediaId: '2', signedUrl: '/media/other.jpg' })],
      }),
    ).toEqual(cover)
  })

  it('resolveCoverFromDetail 应回退到 images 第一张', () => {
    const image = mockMediaFile({ mediaId: '2', signedUrl: '/media/first.jpg' })
    expect(resolveCoverFromDetail({ images: [image] })).toEqual(image)
  })

  it('enrichActivitiesWithCoverImages 应为缺封面项请求详情', async () => {
    vi.mocked(getActivityDetail).mockResolvedValue({
      activityId: 'a1',
      coverImage: mockMediaFile({ mediaId: 'img1', signedUrl: '/media/a1.jpg' }),
      images: [],
    } as never)

    const result = await enrichActivitiesWithCoverImages<ActivitySummary>([
      {
        activityId: 'a1',
        title: '测试',
        tags: [],
        startAt: '2026-07-01T10:00:00+08:00',
        endAt: '2026-07-01T12:00:00+08:00',
        location: {
          city: '北京市',
          address: '测试地址',
          point: { longitude: 116.4, latitude: 39.9 },
        },
        capacity: 10,
        registeredCount: 0,
        occupiedCount: 0,
        runtimeStatus: 'registering',
        reviewStatus: 'approved',
        requireLocationCheck: false,
      },
    ])

    expect(getActivityDetail).toHaveBeenCalledWith('a1')
    expect(result[0].coverImage?.signedUrl).toBe('/media/a1.jpg')
  })

  it('已有封面时不应重复请求详情', async () => {
    const result = await enrichActivitiesWithCoverImages([
      {
        activityId: 'a2',
        coverImage: mockMediaFile({ mediaId: 'img2', signedUrl: '/media/a2.jpg' }),
      },
    ])

    expect(getActivityDetail).not.toHaveBeenCalled()
    expect(result[0].coverImage?.signedUrl).toBe('/media/a2.jpg')
  })
})

import { describe, expect, it } from 'vitest'
import { buildSearchActivitiesQuery, getTimeRange, hasSearchFilters } from '@/utils/search-filters'

describe('search-filters', () => {
  it('hasSearchFilters 应在无筛选时返回 false', () => {
    expect(
      hasSearchFilters({
        activityTypes: [],
        city: null,
        fee: null,
        time: null,
        distanceMeters: null,
        location: null,
      }),
    ).toBe(false)
  })

  it('buildSearchActivitiesQuery 应输出 OpenAPI 筛选参数', () => {
    const query = buildSearchActivitiesQuery(
      '羽毛球',
      {
        activityTypes: ['运动', '户外'],
        city: '北京',
        fee: 'free',
        time: 'today',
        distanceMeters: 3000,
        location: { longitude: 116.4, latitude: 39.9 },
      },
      1,
      20,
    )

    expect(query).toMatchObject({
      keyword: '羽毛球',
      activityTypes: ['运动', '户外'],
      city: '北京',
      minFee: 0,
      maxFee: 0,
      longitude: 116.4,
      latitude: 39.9,
      distanceMeters: 3000,
      page: 1,
      pageSize: 20,
    })
    expect(query.startAtFrom).toMatch(/\+08:00$/)
    expect(query.startAtTo).toMatch(/\+08:00$/)
  })

  it('付费筛选应设置 minFee 下限', () => {
    const query = buildSearchActivitiesQuery(
      '',
      {
        activityTypes: [],
        city: null,
        fee: 'paid',
        time: null,
        distanceMeters: null,
        location: null,
      },
      1,
      10,
    )

    expect(query.minFee).toBe(0.01)
    expect(query.maxFee).toBeUndefined()
  })

  it('距离筛选缺少定位时不应输出距离 query', () => {
    const query = buildSearchActivitiesQuery(
      '',
      {
        activityTypes: [],
        city: null,
        fee: null,
        time: null,
        distanceMeters: 5000,
        location: null,
      },
      1,
      10,
    )

    expect(query.longitude).toBeUndefined()
    expect(query.latitude).toBeUndefined()
    expect(query.distanceMeters).toBeUndefined()
  })

  it('getTimeRange 应按 UTC+8 固定计算本月边界', () => {
    const range = getTimeRange('month', new Date('2026-07-05T16:30:00.000Z'))

    expect(range).toEqual({
      startAtFrom: '2026-07-01T00:00:00+08:00',
      startAtTo: '2026-07-31T23:59:59+08:00',
    })
  })

  it('getTimeRange 应按 UTC+8 固定计算跨月本周边界', () => {
    const range = getTimeRange('week', new Date('2026-08-31T16:30:00.000Z'))

    expect(range).toEqual({
      startAtFrom: '2026-08-31T00:00:00+08:00',
      startAtTo: '2026-09-06T23:59:59+08:00',
    })
  })
})

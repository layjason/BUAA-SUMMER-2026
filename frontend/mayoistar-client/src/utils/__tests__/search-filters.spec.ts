import { describe, expect, it } from 'vitest'
import { buildSearchActivitiesQuery, getTimeRange, hasSearchFilters } from '@/utils/search-filters'

describe('search-filters', () => {
  it('hasSearchFilters 应在无筛选时返回 false', () => {
    expect(
      hasSearchFilters({
        activityType: null,
        city: null,
        fee: null,
        time: null,
      }),
    ).toBe(false)
  })

  it('buildSearchActivitiesQuery 应输出 OpenAPI 筛选参数', () => {
    const query = buildSearchActivitiesQuery(
      '羽毛球',
      {
        activityType: '运动',
        city: '北京',
        fee: 'free',
        time: 'today',
      },
      1,
      20,
    )

    expect(query).toMatchObject({
      keyword: '羽毛球',
      activityTypes: ['运动'],
      city: '北京',
      minFee: 0,
      maxFee: 0,
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
        activityType: null,
        city: null,
        fee: 'paid',
        time: null,
      },
      1,
      10,
    )

    expect(query.minFee).toBe(0.01)
    expect(query.maxFee).toBeUndefined()
  })

  it('getTimeRange 应返回本月范围', () => {
    const range = getTimeRange('month', new Date('2026-07-15T12:00:00+08:00'))

    expect(range.startAtFrom).toContain('2026-07-01T00:00:00+08:00')
    expect(range.startAtTo).toContain('2026-07-31T23:59:59+08:00')
  })
})

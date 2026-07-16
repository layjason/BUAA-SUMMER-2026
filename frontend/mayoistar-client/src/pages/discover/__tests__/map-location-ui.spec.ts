import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('地图模式定位参数', () => {
  it('应先用默认中心加载，再用定位结果请求符合 ActivitySearchQuery 的地图点位', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/map.vue'), 'utf8')

    expect(source).toContain('DEFAULT_CENTER')
    expect(source).toContain('DEFAULT_DISTANCE_METERS')
    expect(source).toContain('getCurrentCenter')
    expect(source).toContain('timeout: 8000')
    expect(source).toContain('routeFilters.value')
    expect(source).toContain('getMapActivities({')
    expect(source).toContain('page: 1')
    expect(source).toContain('pageSize: 100')
    expect(source).toContain('longitude: centerLongitude.value')
    expect(source).toContain('latitude: centerLatitude.value')
    expect(source).toContain('distanceMeters: distanceMeters.value')
    expect(source).not.toContain('getMapActivities(116.4, 39.9, 10000)')
  })
})

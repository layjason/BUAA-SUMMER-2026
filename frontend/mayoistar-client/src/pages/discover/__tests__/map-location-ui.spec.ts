import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('地图模式定位参数', () => {
  it('应优先使用当前位置请求符合 ActivitySearchQuery 的地图点位', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/map.vue'), 'utf8')

    expect(source).toContain('getCurrentLocation')
    expect(source).toContain('fallbackLocation')
    expect(source).toContain('mapDistanceMeters')
    expect(source).toContain('getMapActivities({')
    expect(source).toContain('longitude: center.longitude')
    expect(source).toContain('latitude: center.latitude')
    expect(source).toContain('distanceMeters: mapDistanceMeters')
    expect(source).not.toContain('getMapActivities(116.4, 39.9, 10000)')
  })
})

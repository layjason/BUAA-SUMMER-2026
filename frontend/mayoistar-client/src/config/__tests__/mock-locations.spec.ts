import { describe, expect, it } from 'vitest'
import { MOCK_ACTIVITY_LOCATIONS } from '@/config/mock-locations'

describe('MOCK_ACTIVITY_LOCATIONS', () => {
  it('应包含 4 个预设地点且坐标不再使用固定 116.4/39.9', () => {
    expect(MOCK_ACTIVITY_LOCATIONS).toHaveLength(4)
    expect(MOCK_ACTIVITY_LOCATIONS.map((item) => item.placeName)).toEqual([
      '奥林匹克体育中心',
      '国贸地铁站',
      '王府井书店',
      '朝阳公园',
    ])

    for (const location of MOCK_ACTIVITY_LOCATIONS) {
      expect(location.point.longitude).not.toBe(116.4)
      expect(location.point.latitude).not.toBe(39.9)
      expect(location.city).toBeTruthy()
      expect(location.address).toBeTruthy()
    }
  })
})

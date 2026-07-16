import { describe, expect, it } from 'vitest'
import { ALL_SEARCH_CITIES, SEARCH_CITY_GROUPS } from '@/config/search-cities'

describe('search-cities', () => {
  it('应包含一线、新一线、二线共 49 个城市', () => {
    const total = SEARCH_CITY_GROUPS.reduce((sum, group) => sum + group.cities.length, 0)
    expect(total).toBe(49)
    expect(ALL_SEARCH_CITIES).toHaveLength(49)
  })

  it('一线城市应包含上海、北京、深圳、广州', () => {
    expect(SEARCH_CITY_GROUPS[0].cities).toEqual(['上海', '北京', '深圳', '广州'])
  })
})

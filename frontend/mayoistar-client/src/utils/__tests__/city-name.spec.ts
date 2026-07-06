import { describe, expect, it } from 'vitest'
import { normalizeCityForSearch } from '@/utils/city-name'

describe('city-name', () => {
  it('应为筛选 chips 补上末尾「市」', () => {
    expect(normalizeCityForSearch('北京')).toBe('北京市')
    expect(normalizeCityForSearch('上海')).toBe('上海市')
    expect(normalizeCityForSearch('广州')).toBe('广州市')
  })

  it('应保留高德返回的带「市」城市名', () => {
    expect(normalizeCityForSearch('北京市')).toBe('北京市')
    expect(normalizeCityForSearch('上海市')).toBe('上海市')
  })

  it('应保留省级行政区名称', () => {
    expect(normalizeCityForSearch('河北省')).toBe('河北省')
  })

  it('空字符串应返回空', () => {
    expect(normalizeCityForSearch('')).toBe('')
    expect(normalizeCityForSearch('   ')).toBe('')
  })
})

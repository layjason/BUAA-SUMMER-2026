import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('首页附近 Tab 定位参数', () => {
  it('应优先使用真实定位并提供筛选入口', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/home/index.vue'), 'utf8')

    expect(source).toContain('getCurrentLocation')
    expect(source).toContain('ensureNearbyLocation')
    expect(source).toContain('nearbyFallbackLocation')
    expect(source).toContain('goSearch')
  })

  it('首页应提供明显搜索、高级筛选和附近地图入口', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/home/index.vue'), 'utf8')

    expect(source).toContain('class="home-search-entry"')
    expect(source).toContain('搜索活动名称、标签...')
    expect(source).toContain('高级筛选')
    expect((source.match(/高级筛选/g) ?? []).length).toBe(1)
    expect(source).toContain('类型、时间、城市、费用、距离')
    expect(source).toContain("activeTab === 'nearby'")
    expect(source).toContain('地图模式')
    expect(source).toContain("url: '/pages/discover/search'")
    expect(source).toContain("url: '/pages/discover/map'")
  })
})

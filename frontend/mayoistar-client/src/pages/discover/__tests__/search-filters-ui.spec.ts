import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('搜索页轻量筛选 UI', () => {
  it('应包含筛选 chips 并调用 buildSearchActivitiesQuery', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/search.vue'), 'utf8')

    expect(source).toContain('buildSearchActivitiesQuery')
    expect(source).toContain('运动')
    expect(source).toContain('北京')
    expect(source).toContain('免费')
    expect(source).toContain('本周')
    expect(source).toContain('toggleTypeFilter')
    expect(source).toContain('selectedTypes.includes(type)')
    expect(source).toContain('DISTANCE_OPTIONS')
    expect(source).toContain('toggleDistanceFilter')
    expect(source).toContain('getCurrentLocation')
  })

  it('应提供显式关键词搜索按钮', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/search.vue'), 'utf8')

    expect(source).toContain('class="search-submit"')
    expect(source).toContain('@tap="doSearch"')
    expect(source).toContain('>搜索</text>')
  })
})

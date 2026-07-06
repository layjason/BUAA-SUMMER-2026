import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('搜索页轻量筛选 UI', () => {
  it('应包含筛选 chips 并调用 buildSearchActivitiesQuery', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/search.vue'), 'utf8')

    expect(source).toContain('buildSearchActivitiesQuery')
    expect(source).toContain('getInterestTags')
    expect(source).toContain('typeOptions')
    expect(source).toContain('SEARCH_CITY_GROUPS')
    expect(source).toContain('city-chip-grid')
    expect(source).toContain('city-tier-label')
    expect(source).toContain('toggleCityFilterPanel')
    expect(source).toContain('city-filter-arrow')
    expect(source).toContain('isCityFilterExpanded')
    expect(source).toContain('免费')
    expect(source).toContain('本周')
    expect(source).toContain('toggleTypeFilter')
    expect(source).toContain('selectedTypes.includes(type)')
    expect(source).toContain('DISTANCE_OPTIONS')
    expect(source).toContain('toggleDistanceFilter')
    expect(source).toContain('getCurrentLocation')
    expect(source).toContain('resolveDetectedCity')
    expect(source).toContain('enrichActivitiesWithCoverImages')
    expect(source).toContain('getCoverDisplayUrl')
    expect(source).toContain('detectedCity')
    expect(source).not.toContain('const TYPE_OPTIONS')
  })

  it('应提供显式关键词搜索按钮', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/search.vue'), 'utf8')

    expect(source).toContain('class="search-submit"')
    expect(source).toContain('@tap="doSearch"')
    expect(source).toContain('>搜索</text>')
  })

  it('应使用 OpenAPI 分页参数加载更多搜索结果', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/search.vue'), 'utf8')

    expect(source).toContain('currentPage')
    expect(source).toContain('pageSize')
    expect(source).toContain('loadMoreSearchResults')
    expect(source).toContain('@scrolltolower="loadMoreSearchResults"')
    expect(source).toContain('nextPage')
  })

  it('筛选区与结果区应使用独立滚动避免溢出屏幕', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/search.vue'), 'utf8')

    expect(source).toContain('class="filter-panel" scroll-y')
    expect(source).toContain('class="search-content"')
    expect(source).toContain('grid-template-columns: repeat(4, minmax(0, 1fr))')
  })
})

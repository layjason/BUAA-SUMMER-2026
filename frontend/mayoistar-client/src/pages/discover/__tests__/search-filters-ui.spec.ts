import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('搜索页轻量筛选 UI', () => {
  it('应包含筛选 chips 并调用 buildSearchActivitiesQuery', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/search.vue'), 'utf8')

    expect(source).toContain('buildSearchActivitiesQuery')
    expect(source).toContain('getInterestTags')
    expect(source).toContain('typeOptions')
    expect(source).toContain('北京')
    expect(source).toContain('免费')
    expect(source).toContain('本周')
    expect(source).toContain('toggleTypeFilter')
    expect(source).toContain('selectedTypes.includes(type)')
    expect(source).toContain('DISTANCE_OPTIONS')
    expect(source).toContain('toggleDistanceFilter')
    expect(source).toContain('getCurrentLocation')
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

  it('切换地图模式时应携带当前高级筛选条件', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/discover/search.vue'), 'utf8')
    const mapQueryBody = source.slice(
      source.indexOf('function buildMapQueryString('),
      source.indexOf('/**\n * 跳转到地图模式。'),
    )
    const goToMapBody = source.slice(
      source.indexOf('function goToMap(): void {'),
      source.indexOf('/** 跳转到活动详情 */'),
    )

    expect(source).toContain('function buildMapQueryString')
    expect(mapQueryBody).toContain("key === 'page' || key === 'pageSize'")
    expect(mapQueryBody).toContain("Array.isArray(value) ? value.join(',')")
    expect(goToMapBody).toContain('buildSearchActivitiesQuery(keyword.value, getFilterSelection()')
    expect(goToMapBody).toContain('buildMapQueryString(query)')
    expect(goToMapBody).toContain('/pages/discover/map${queryString ? `?${queryString}` :')
  })
})

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
  })
})

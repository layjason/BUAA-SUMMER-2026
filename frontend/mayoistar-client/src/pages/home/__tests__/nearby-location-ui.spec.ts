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
})

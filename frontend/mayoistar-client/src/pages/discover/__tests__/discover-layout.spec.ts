import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function readDiscoverPage(filename: string): string {
  return readFileSync(resolve(process.cwd(), 'src/pages/discover', filename), 'utf8')
}

describe('发现页移动端滚动布局', () => {
  it('推荐列表应位于内部 scroll-view 中，避免被 tabBar 截断', () => {
    const source = readDiscoverPage('index.vue')

    expect(source).toContain('<scroll-view class="discover-scroll" scroll-y>')
    expect(source).toContain('<view class="discover-content">')
    expect(source).toContain('.discover-scroll')
    expect(source).toContain('flex: 1;')
    expect(source).toContain('overflow: hidden;')
    expect(source).toContain(
      'padding-bottom: calc(#{$safe-bottom} + #{$tabbar-height} + #{$spacing-xl});',
    )
  })
})

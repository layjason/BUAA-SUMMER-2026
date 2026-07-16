import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function readHomePage(): string {
  return readFileSync(resolve(process.cwd(), 'src/pages/home/index.vue'), 'utf8')
}

describe('首页创建活动入口', () => {
  it('首页不应展示创建活动悬浮按钮', () => {
    const source = readHomePage()

    expect(source).not.toContain('class="fab"')
    expect(source).not.toContain('function goCreate')
    expect(source).not.toContain('/pages/activity/templates')
  })
})

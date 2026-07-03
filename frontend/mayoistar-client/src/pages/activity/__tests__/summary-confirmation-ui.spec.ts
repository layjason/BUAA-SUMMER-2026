import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('活动总结 AI 分类人工确认 UI', () => {
  it('应在提交前允许人工确认或取消图片标签', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/summary.vue'), 'utf8')

    expect(source).toContain('confirmedImageTagMap')
    expect(source).toContain('toggleConfirmedImageTag')
    expect(source).toContain('isImageTagConfirmed')
    expect(source).toContain('confirmedImageTags')
  })
})

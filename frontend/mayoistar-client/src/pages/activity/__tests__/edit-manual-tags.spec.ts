import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('活动编辑页手动标签', () => {
  it('应由用户手动新增标签，不再读取后端标签列表', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/edit.vue'), 'utf8')

    expect(source).toContain('tagInput')
    expect(source).toContain('function addTag')
    expect(source).toContain('function removeTag')
    expect(source).toContain('activityTags')
    expect(source).not.toContain('getInterestTags')
    expect(source).not.toContain('availableTags')
    expect(source).not.toContain('loadTags')
  })
})

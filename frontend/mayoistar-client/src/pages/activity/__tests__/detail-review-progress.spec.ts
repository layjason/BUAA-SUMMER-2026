import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('活动详情页审核进度加载', () => {
  it('审核中活动应先加载核心详情，附属总结评价不应阻断页面展示', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/detail.vue'), 'utf8')
    const loadDataBody = source.slice(
      source.indexOf('async function loadData(): Promise<void> {'),
      source.indexOf('onLoad((query) => {'),
    )

    expect(source).toContain('async function loadPublishedActivityExtras(): Promise<void>')
    expect(loadDataBody).toContain('getActivityDetail(activityId.value)')
    expect(loadDataBody).toContain("if (act.reviewStatus === 'approved')")
    expect(loadDataBody).not.toContain('getActivitySummaries(activityId.value, 1, 5)')
    expect(loadDataBody).not.toContain('getActivityReviews(activityId.value, 1, 10)')
    expect(loadDataBody).not.toContain('getMyActivityReview(activityId.value)')
  })
})

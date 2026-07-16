import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('活动详情页审核进度加载', () => {
  it('活动无可预览图片时不应渲染图片占位区域', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/detail.vue'), 'utf8')

    expect(source).toContain('v-if="activityImagePreviews.length > 0"')
    expect(source).not.toContain('class="image-placeholder"')
    expect(source).not.toContain('.image-placeholder')
  })

  it('未登录查看活动详情时不应请求参与状态或我的评价状态', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/detail.vue'), 'utf8')
    const loadDataBody = source.slice(
      source.indexOf('async function loadData(): Promise<void> {'),
      source.indexOf('onLoad((query) => {'),
    )
    const extrasBody = source.slice(
      source.indexOf('async function loadPublishedActivityExtras(): Promise<void> {'),
      source.indexOf('/**\n * 加载详情页全部数据。'),
    )

    expect(loadDataBody).toContain('const act = await getActivityDetail(activityId.value)')
    expect(loadDataBody).toContain('authStore.isLoggedIn')
    expect(loadDataBody).toContain('await fetchParticipationState(activityId.value)')
    expect(extrasBody).toContain('if (authStore.isLoggedIn)')
    expect(extrasBody).toContain('await getMyActivityReview(activityId.value)')
  })

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

  it('审核原因应按 reviewedAt 降序选取最新评审意见', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/detail.vue'), 'utf8')
    const latestRecordBody = source.slice(
      source.indexOf(
        'function getLatestReviewRecord(records: ReviewRecord[]): ReviewRecord | null {',
      ),
      source.indexOf('/** 审核原因，仅驳回和要求修改时展示。 */'),
    )
    const reviewReasonBody = source.slice(
      source.indexOf('const reviewReason = computed(() => {'),
      source.indexOf('/** 是否展示审核原因。 */'),
    )

    expect(latestRecordBody).toContain('[...records].sort')
    expect(latestRecordBody).toContain('new Date(right.reviewedAt).getTime()')
    expect(latestRecordBody).toContain('return rightTime - leftTime')
    expect(reviewReasonBody).toContain('getLatestReviewRecord(records)?.reason')
    expect(reviewReasonBody).not.toContain('records[records.length - 1]?.reason')
  })

  it('签到二维码按钮禁用态应覆盖主按钮和 ghost 按钮背景', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/activity/detail.vue'), 'utf8')
    const disabledSelectorPattern =
      String.raw`\.checkin-action\[disabled\],[\s\S]*` +
      String.raw`\.checkin-action-ghost\[disabled\]\s*\{[\s\S]*`

    expect(source).toContain('.checkin-action[disabled],\n.checkin-action-ghost[disabled]')
    expect(source).toMatch(
      new RegExp(`${disabledSelectorPattern}background:\\s*var\\(--q-color-bg-soft\\);`),
    )
    expect(source).toMatch(new RegExp(`${disabledSelectorPattern}opacity:\\s*1;`))
  })
})

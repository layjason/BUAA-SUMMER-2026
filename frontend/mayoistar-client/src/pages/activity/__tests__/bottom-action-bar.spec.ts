import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function readActivityPage(filename: string): string {
  return readFileSync(resolve(process.cwd(), 'src/pages/activity', filename), 'utf8')
}

describe('活动页面底部操作栏', () => {
  it('编辑页应显式导入 BottomActionBar 以确保样式组件被解析', () => {
    const source = readActivityPage('edit.vue')

    expect(source).toContain('<BottomActionBar>')
    expect(source).toMatch(/import\s+\{[^}]*BottomActionBar[^}]*}\s+from\s+['"]@\/components['"]/)
  })

  it('模板选择页应使用固定底部操作栏，避免按钮被手机安全区遮挡', () => {
    const source = readActivityPage('templates.vue')

    expect(source).toContain('<BottomActionBar>')
    expect(source).toMatch(/import\s+\{[^}]*BottomActionBar[^}]*}\s+from\s+['"]@\/components['"]/)
    expect(source).not.toContain('class="action-bar"')
  })

  it('模板选择页模板卡片应使用稳定两列布局', () => {
    const source = readActivityPage('templates.vue')

    expect(source).toContain('templateRows')
    expect(source).toContain('class="template-row"')
    expect(source).toContain('class="card-inner"')
    expect(source).toMatch(/\.template-row\s*\{[\s\S]*display:\s*flex;/)
    expect(source).toMatch(/\.card\s*\{[\s\S]*width:\s*50%;[\s\S]*padding:\s*0 8rpx;/)
    expect(source).not.toMatch(/\.template-grid\s*\{[\s\S]*gap:/)
  })

  it('详情页主操作按钮应复用固定底部操作栏样式', () => {
    const source = readActivityPage('detail.vue')

    expect(source).toContain('<BottomActionBar v-if=')
    expect(source).toMatch(/import\s+\{[^}]*BottomActionBar[^}]*}\s+from\s+['"]@\/components['"]/)
    expect(source).toContain('class="bar-btn bar-btn-primary"')
    expect(source).not.toContain('class="action-bar"')
    expect(source).not.toContain('class="action-btn"')
  })

  it('详情页主按钮应让报名中未来活动优先取消报名而不是签到', () => {
    const source = readActivityPage('detail.vue')
    const buttonTextBody = source.slice(
      source.indexOf('const buttonText = computed(() => {'),
      source.indexOf('const buttonDisabled = computed(() => {'),
    )
    const handleActionBody = source.slice(
      source.indexOf('function handleAction(): void {'),
      source.indexOf('/**\n * 取消报名确认弹窗'),
    )

    expect(buttonTextBody.indexOf("p.status === 'registered'")).toBeLessThan(
      buttonTextBody.indexOf('p.canCheckIn'),
    )
    expect(handleActionBody.indexOf("p.status === 'registered'")).toBeLessThan(
      handleActionBody.indexOf('p.canCheckIn'),
    )
  })

  it('详情页评价入口应使用后端参与状态控制并展示截止时间', () => {
    const source = readActivityPage('detail.vue')

    expect(source).toContain(
      'const canReview = computed(() => participation.value?.canReview === true)',
    )
    expect(source).not.toContain(
      'const canReview = computed(() => meetsReviewCondition.value && !hasReviewed.value)',
    )
    expect(source).toContain('reviewWindowEndsAt')
    expect(source).toContain('reviewDeadlineText')
  })

  it('详情页活动总结和评价条目应可进入详情页', () => {
    const source = readActivityPage('detail.vue')

    expect(source).toContain('@click="goSummaryDetail(item.summaryId)"')
    expect(source).toContain('@click="goReviewDetail(item.reviewId)"')
    expect(source).toContain('function goSummaryDetail(summaryId: string): void')
    expect(source).toContain('function goReviewDetail(reviewId: string): void')
    expect(source).toContain('/pages/activity/summary-detail?activityId=')
    expect(source).toContain('/pages/activity/review-detail?activityId=')
    expect(source).toContain('published-block--link')
  })

  it('活动总结和评价详情页应通过列表接口按 ID 回查详情', () => {
    const summarySource = readActivityPage('summary-detail.vue')
    const reviewSource = readActivityPage('review-detail.vue')
    const pagesConfig = readFileSync(resolve(process.cwd(), 'src/pages.json'), 'utf8')

    expect(pagesConfig).toContain('pages/activity/summary-detail')
    expect(pagesConfig).toContain('pages/activity/review-detail')
    expect(summarySource).toContain('getActivitySummaries')
    expect(summarySource).toContain('item.summaryId === summaryId.value')
    expect(summarySource).toContain('summary.imageTags')
    expect(reviewSource).toContain('getActivityReviews')
    expect(reviewSource).toContain('item.reviewId === reviewId.value')
    expect(reviewSource).toContain('review.rating')
    expect(reviewSource).toContain('markdownImagePattern')
    expect(reviewSource).toContain('reviewImageUrls')
    expect(reviewSource).toContain('reviewTextContent')
    expect(reviewSource).toContain('content.replace(markdownImagePattern')
    expect(reviewSource).toContain('class="review-image"')
  })
})

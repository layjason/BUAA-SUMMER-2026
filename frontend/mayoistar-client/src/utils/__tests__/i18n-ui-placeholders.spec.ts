import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function readSource(path: string): string {
  return readFileSync(resolve(process.cwd(), path), 'utf8')
}

describe('UI 命名占位符插值', () => {
  it('应在页面侧显式格式化命名占位符，避免原生端外显模板文本', () => {
    const homeSource = readSource('src/pages/home/index.vue')
    const reviewSource = readSource('src/pages/activity/review.vue')
    const reviewDetailSource = readSource('src/pages/activity/review-detail.vue')

    expect(homeSource).toContain('function formatRegisteredText(count: number, total: number)')
    expect(homeSource).toContain("formatI18nTemplate(String(tm('home.registered'))")
    expect(homeSource).not.toContain("t('home.registered', {")

    expect(reviewSource).toContain("formatI18nTemplate(String(tm('activityReview.deadline'))")
    expect(reviewSource).not.toContain("t('activityReview.deadline', {")

    expect(reviewDetailSource).toContain('function reviewRatingText(rating: number): string')
    expect(reviewDetailSource).toContain(
      "formatI18nTemplate(String(tm('activityDetail.reviewStars'))",
    )
    expect(reviewDetailSource).not.toContain("t('activityDetail.reviewStars', {")
  })
})
